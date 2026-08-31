package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.DmsAdminSession;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopMemberSession;
import com.macro.mall.distribution.service.impl.AdminAuthServiceImpl;
import com.macro.mall.distribution.service.impl.ShopAuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityHardeningTest {

    @Mock private DmsAdminUserDao adminUserDao;
    @Mock private DmsAdminSessionDao adminSessionDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsShopMemberSessionDao memberSessionDao;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AgentService agentService;
    @Mock private LoginCaptchaService captchaService;
    @Mock private SmsVerificationService smsVerificationService;

    @Test
    void adminLegacyPasswordUpgradesToBcryptAndStoresOnlyTokenHash() {
        String password = "Legacy-password-123";
        String salt = "legacy-salt";
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9L);
        admin.setUsername("operator");
        admin.setPasswordHash(SecureUtil.sha256(password + ":" + salt));
        admin.setSalt(salt);
        admin.setStatus(1);
        admin.setPermissions("admin:read");
        when(adminUserDao.selectByUsernameAndPortal("operator", "PLATFORM")).thenReturn(admin);

        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator");
        dto.setPassword(password);
        dto.setCaptchaId("captcha-id");
        dto.setCaptchaCode("8A2K");
        dto.setPortal("PLATFORM");
        LocalDateTime expectedExpireAfter = LocalDateTime.now().plusDays(7);
        var result = service.login(dto);

        verify(captchaService).verify("admin", "captcha-id", "8A2K");
        verify(adminSessionDao).selectActiveByAdminId(9L);
        verify(adminSessionDao, never()).disableByAdminId(9L);

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(adminUserDao).updatePassword(eq(9L), passwordHash.capture(), eq("BCRYPT"), eq(0));
        assertTrue(BCrypt.checkpw(password, passwordHash.getValue()));

        ArgumentCaptor<DmsAdminSession> session = ArgumentCaptor.forClass(DmsAdminSession.class);
        verify(adminSessionDao).insert(session.capture());
        assertNotEquals(result.getToken(), session.getValue().getToken());
        assertEquals(SecureUtil.sha256(result.getToken()), session.getValue().getToken());
        assertFalse(session.getValue().getExpireTime().isBefore(expectedExpireAfter));
        assertFalse(session.getValue().getExpireTime().isAfter(LocalDateTime.now().plusDays(7)));
        assertEquals(session.getValue().getExpireTime(), result.getExpireTime());
    }

    @Test
    void platformAndMerchantAccountsCannotUseTheWrongLoginPortal() {
        String password = "Valid-password-123";
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);

        AdminLoginDTO platformAtMerchantPortal = new AdminLoginDTO();
        platformAtMerchantPortal.setUsername("platform-admin");
        platformAtMerchantPortal.setPassword(password);
        platformAtMerchantPortal.setCaptchaId("captcha-platform");
        platformAtMerchantPortal.setCaptchaCode("8A2K");
        platformAtMerchantPortal.setPortal("MERCHANT");
        RuntimeException platformBlocked = assertThrows(RuntimeException.class,
                () -> service.login(platformAtMerchantPortal));
        assertEquals("账号或密码错误", platformBlocked.getMessage());

        AdminLoginDTO merchantAtPlatformPortal = new AdminLoginDTO();
        merchantAtPlatformPortal.setUsername("merchant-admin");
        merchantAtPlatformPortal.setPassword(password);
        merchantAtPlatformPortal.setCaptchaId("captcha-merchant");
        merchantAtPlatformPortal.setCaptchaCode("8A2K");
        merchantAtPlatformPortal.setPortal("PLATFORM");
        RuntimeException merchantBlocked = assertThrows(RuntimeException.class,
                () -> service.login(merchantAtPlatformPortal));
        assertEquals("账号或密码错误", merchantBlocked.getMessage());
        verify(adminUserDao).selectByUsernameAndPortal("platform-admin", "MERCHANT");
        verify(adminUserDao).selectByUsernameAndPortal("merchant-admin", "PLATFORM");
        verify(adminUserDao, never()).selectByUsername(anyString());
        verify(adminSessionDao, never()).insert(any(DmsAdminSession.class));
    }

    @Test
    void adminLoginStopsBeforePasswordLookupWhenCaptchaFails() {
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator");
        dto.setPassword("password");
        dto.setCaptchaId("expired-captcha");
        dto.setCaptchaCode("0000");
        dto.setPortal("PLATFORM");
        doThrow(new IllegalArgumentException("图形验证码错误或已过期"))
                .when(captchaService).verify("admin", "expired-captcha", "0000");

        assertThrows(IllegalArgumentException.class, () -> service.login(dto));
        verifyNoInteractions(adminUserDao, adminSessionDao);
    }

    @Test
    void activeAdminLockIsTemporaryAndDoesNotRevealAccountState() {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9L);
        admin.setUsername("operator");
        admin.setLockTime(LocalDateTime.now().minusMinutes(1));
        when(adminUserDao.selectByUsernameAndPortal("operator", "PLATFORM")).thenReturn(admin);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator"); dto.setPassword("wrong-password");
        dto.setCaptchaId("captcha"); dto.setCaptchaCode("8A2K");
        dto.setPortal("PLATFORM");

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.login(dto));
        assertEquals("账号或密码错误", error.getMessage());
    }

    @Test
    void expiredAdminLockIsClearedBeforePasswordVerification() {
        String password = "Valid-password-123";
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9L); admin.setUsername("operator"); admin.setStatus(1);
        admin.setPermissions("admin:read"); admin.setSalt("BCRYPT");
        admin.setPasswordHash(BCrypt.hashpw(password));
        admin.setLockTime(LocalDateTime.now().minusMinutes(16));
        when(adminUserDao.selectByUsernameAndPortal("operator", "PLATFORM")).thenReturn(admin);
        when(adminUserDao.clearExpiredLoginLock(eq(9L), any(LocalDateTime.class))).thenReturn(1);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator"); dto.setPassword(password);
        dto.setCaptchaId("captcha"); dto.setCaptchaCode("8A2K");
        dto.setPortal("PLATFORM");

        service.login(dto);

        verify(adminUserDao).clearExpiredLoginLock(eq(9L), any(LocalDateTime.class));
        verify(adminSessionDao).insert(any(DmsAdminSession.class));
    }

    @Test
    void memberLoginStoresOnlyTokenHash() {
        String password = "Member-password-123";
        DmsShopMember member = new DmsShopMember();
        member.setId(12L);
        member.setUserId(1200L);
        member.setPhone("13900000000");
        member.setPasswordHash(BCrypt.hashpw(password));
        member.setStatus(1);
        when(memberDao.selectByAccount(member.getPhone())).thenReturn(member);

        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, memberSessionDao,
                agentService, captchaService, smsVerificationService,
                mock(com.macro.mall.distribution.dao.DmsTenantDao.class), mock(MemberMessageService.class));
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount(member.getPhone());
        dto.setPassword(password);
        dto.setLoginType("password");
        var result = service.login(dto);

        verify(memberSessionDao).selectActiveByMemberId(12L);
        verify(memberSessionDao, never()).disableByMemberId(12L);
        ArgumentCaptor<DmsShopMemberSession> session = ArgumentCaptor.forClass(DmsShopMemberSession.class);
        verify(memberSessionDao).insert(session.capture());
        assertNotEquals(result.getToken(), session.getValue().getToken());
        assertEquals(SecureUtil.sha256(result.getToken()), session.getValue().getToken());
        assertEquals("integrated", session.getValue().getSurface());
        assertFalse(session.getValue().getExpireTime().isBefore(LocalDateTime.now().plusDays(30).minusSeconds(2)));
    }

    @Test
    void adminLoginKeepsThreeRecentDevicesAndOnlyRevokesTheOldestOne() {
        String password = "Admin-password-123";
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(21L);
        admin.setUsername("private-admin");
        admin.setPasswordHash(BCrypt.hashpw(password));
        admin.setSalt("BCRYPT");
        admin.setStatus(1);
        when(adminUserDao.selectByUsernameAndPortal("private-admin", "PLATFORM")).thenReturn(admin);
        when(adminSessionDao.selectActiveByAdminId(21L)).thenReturn(List.of(
                adminSession("newest"), adminSession("second"), adminSession("third"), adminSession("oldest")));

        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("private-admin");
        dto.setPassword(password);
        dto.setPortal("PLATFORM");
        new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService).login(dto);

        verify(adminSessionDao).disableByToken("oldest");
        verify(adminSessionDao, never()).disableByToken("newest");
        verify(adminSessionDao, never()).disableByToken("second");
        verify(adminSessionDao, never()).disableByToken("third");
    }

    @Test
    void memberLoginKeepsFiveRecentDevicesAndOnlyRevokesTheOldestOne() {
        String password = "Member-password-123";
        DmsShopMember member = new DmsShopMember();
        member.setId(22L);
        member.setUserId(2200L);
        member.setPhone("13700000000");
        member.setPasswordHash(BCrypt.hashpw(password));
        member.setStatus(1);
        when(memberDao.selectByAccount(member.getPhone())).thenReturn(member);
        when(memberSessionDao.selectActiveByMemberId(22L)).thenReturn(List.of(
                memberSession("newest"), memberSession("second"), memberSession("third"),
                memberSession("fourth"), memberSession("fifth"), memberSession("oldest")));

        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount(member.getPhone());
        dto.setPassword(password);
        dto.setLoginType("password");
        new ShopAuthServiceImpl(memberDao, memberSessionDao, agentService, captchaService,
                smsVerificationService, mock(com.macro.mall.distribution.dao.DmsTenantDao.class),
                mock(MemberMessageService.class)).login(dto);

        verify(memberSessionDao).disableByToken("oldest");
        verify(memberSessionDao, never()).disableByToken("newest");
        verify(memberSessionDao, never()).disableByToken("second");
        verify(memberSessionDao, never()).disableByToken("third");
        verify(memberSessionDao, never()).disableByToken("fourth");
        verify(memberSessionDao, never()).disableByToken("fifth");
    }

    @Test
    void balanceTransferSurfaceUsesStoredSessionValueInsteadOfRequestHeaders() {
        String rawToken = "surface-bound-session";
        DmsShopMemberSession session = new DmsShopMemberSession();
        session.setSurface("team");
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusHours(1));
        when(memberSessionDao.selectByToken(SecureUtil.sha256(rawToken))).thenReturn(session);
        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, memberSessionDao,
                agentService, captchaService, smsVerificationService,
                mock(com.macro.mall.distribution.dao.DmsTenantDao.class), mock(MemberMessageService.class));

        service.requireSurface("Bearer " + rawToken, "team");
        RuntimeException blocked = assertThrows(RuntimeException.class,
                () -> service.requireSurface("Bearer " + rawToken, "integrated"));
        assertEquals("当前版本不提供余额互转，请使用三合一版", blocked.getMessage());
    }

    @Test
    void memberSessionAcceptsRawTokenButRejectsStoredTokenHashAsBearerCredential() {
        String rawToken = "raw-member-session-token";
        String storedHash = SecureUtil.sha256(rawToken);
        DmsShopMemberSession session = new DmsShopMemberSession();
        session.setMemberId(12L);
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusHours(1));
        DmsShopMember member = new DmsShopMember();
        member.setId(12L);
        member.setStatus(1);
        when(memberSessionDao.selectByToken(storedHash)).thenReturn(session);
        when(memberDao.selectById(12L)).thenReturn(member);
        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, memberSessionDao,
                agentService, captchaService, smsVerificationService,
                mock(com.macro.mall.distribution.dao.DmsTenantDao.class), mock(MemberMessageService.class));

        assertSame(member, service.resolveMember("Bearer " + rawToken));
        assertNull(service.resolveMember("Bearer " + storedHash));
        verify(memberSessionDao).selectByToken(SecureUtil.sha256(storedHash));
        verify(memberSessionDao, times(1)).selectByToken(storedHash);
    }

    @Test
    void adminSessionAcceptsRawTokenButRejectsStoredTokenHashAsBearerCredential() {
        String rawToken = "raw-admin-session-token";
        String storedHash = SecureUtil.sha256(rawToken);
        DmsAdminSession session = new DmsAdminSession();
        session.setAdminId(9L);
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusHours(1));
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9L);
        admin.setStatus(1);
        when(adminSessionDao.selectByToken(storedHash)).thenReturn(session);
        when(adminUserDao.selectById(9L)).thenReturn(admin);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);

        assertSame(admin, service.resolveAdmin("Bearer " + rawToken));
        assertNull(service.resolveAdmin("Bearer " + storedHash));
        verify(adminSessionDao).selectByToken(SecureUtil.sha256(storedHash));
        verify(adminSessionDao, times(1)).selectByToken(storedHash);
    }

    @Test
    void expiredMemberLoginLockClearsAutomaticallyButActiveLockStillBlocksLogin() {
        String password = "Member-password-123";
        DmsShopMember expired = new DmsShopMember();
        expired.setId(12L);
        expired.setUserId(1200L);
        expired.setPhone("13900000000");
        expired.setPasswordHash(BCrypt.hashpw(password));
        expired.setStatus(1);
        expired.setLockTime(LocalDateTime.now().minusMinutes(16));
        when(memberDao.selectByAccount(expired.getPhone())).thenReturn(expired);
        when(memberDao.clearExpiredLoginLock(eq(12L), any(LocalDateTime.class))).thenReturn(1);
        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, memberSessionDao,
                agentService, captchaService, smsVerificationService,
                mock(com.macro.mall.distribution.dao.DmsTenantDao.class), mock(MemberMessageService.class));
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount(expired.getPhone());
        dto.setPassword(password);
        dto.setLoginType("password");

        assertNotNull(service.login(dto));
        verify(memberDao).clearExpiredLoginLock(eq(12L), any(LocalDateTime.class));

        DmsShopMember active = new DmsShopMember();
        active.setId(13L);
        active.setUserId(1300L);
        active.setPhone("13800000000");
        active.setPasswordHash(BCrypt.hashpw(password));
        active.setStatus(1);
        active.setLockTime(LocalDateTime.now().minusMinutes(1));
        when(memberDao.selectByAccount(active.getPhone())).thenReturn(active);
        ShopLoginDTO activeDto = new ShopLoginDTO();
        activeDto.setAccount(active.getPhone());
        activeDto.setPassword(password);
        activeDto.setLoginType("password");

        RuntimeException blocked = assertThrows(RuntimeException.class, () -> service.login(activeDto));
        assertEquals("账号或登录凭证错误", blocked.getMessage());
        verify(memberDao, never()).clearExpiredLoginLock(eq(13L), any(LocalDateTime.class));
        verify(memberSessionDao, never()).disableByMemberId(13L);
    }

    @Test
    void memberStatusUpdateUsesLockedRowAndRejectsUnknownStates() {
        ShopAuthServiceImpl service = new ShopAuthServiceImpl(memberDao, memberSessionDao,
                agentService, captchaService, smsVerificationService,
                mock(com.macro.mall.distribution.dao.DmsTenantDao.class), mock(MemberMessageService.class));
        DmsShopMember member = new DmsShopMember();
        member.setId(12L);
        member.setStatus(1);
        when(memberDao.selectByIdForUpdate(12L)).thenReturn(member);
        when(memberDao.updateStatus(12L, 0)).thenReturn(1);

        assertTrue(service.updateMemberStatus(12L, 0));
        verify(memberDao).selectByIdForUpdate(12L);
        verify(memberDao).updateStatus(12L, 0);
        assertThrows(RuntimeException.class, () -> service.updateMemberStatus(12L, 2));
    }

    @Test
    void financeReauthenticationRequiresCurrentAdminPassword() {
        String password = "Finance-password-123";
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(19L);
        admin.setUsername("finance");
        admin.setPasswordHash(BCrypt.hashpw(password));
        admin.setSalt("BCRYPT");
        admin.setStatus(1);
        when(adminUserDao.selectById(19L)).thenReturn(admin);

        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        service.verifyPassword(admin, password);
        verify(adminUserDao).clearLoginLock(19L);

        assertThrows(RuntimeException.class, () -> service.verifyPassword(admin, "wrong-password"));
        verify(adminUserDao).increaseFailedLogin(19L, 5);
    }

    @Test
    void erpSecretsAreExcludedFromDiagnosticStrings() {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setProviderCode("JUSHUITAN");
        integration.setAppSecret("secret-value");
        integration.setCallbackToken("callback-value");
        assertFalse(integration.toString().contains("secret-value"));
        assertFalse(integration.toString().contains("callback-value"));

        ErpShipmentCallbackDTO callback = new ErpShipmentCallbackDTO();
        callback.setProviderCode("JUSHUITAN");
        callback.setToken("callback-value");
        assertFalse(callback.toString().contains("callback-value"));
    }

    @Test
    void passwordsAndVerificationCodesAreExcludedFromDiagnosticStrings() {
        String secret = "must-not-appear-in-logs";

        AdminMemberCreateDTO adminMember = new AdminMemberCreateDTO();
        adminMember.setPassword(secret);
        assertSensitiveAbsent(adminMember, secret);

        ShopRegisterDTO register = new ShopRegisterDTO();
        register.setPassword(secret);
        register.setSmsCode(secret);
        register.setCaptchaId(secret);
        register.setCaptchaCode(secret);
        assertSensitiveAbsent(register, secret);

        AdminLoginDTO adminLogin = new AdminLoginDTO();
        adminLogin.setPassword(secret);
        adminLogin.setCaptchaCode(secret);
        assertSensitiveAbsent(adminLogin, secret);

        ShopAccountSetupDTO accountSetup = new ShopAccountSetupDTO();
        accountSetup.setPassword(secret);
        assertSensitiveAbsent(accountSetup, secret);

        ShopWithdrawalApplyDTO withdrawal = new ShopWithdrawalApplyDTO();
        withdrawal.setPaymentPassword(secret);
        withdrawal.setSmsCode(secret);
        assertSensitiveAbsent(withdrawal, secret);

        AdminPasswordDTO adminPassword = new AdminPasswordDTO();
        adminPassword.setPassword(secret);
        adminPassword.setCurrentAdminPassword(secret);
        assertSensitiveAbsent(adminPassword, secret);

        AdminUserSaveDTO adminSave = new AdminUserSaveDTO();
        adminSave.setPassword(secret);
        adminSave.setCurrentAdminPassword(secret);
        assertSensitiveAbsent(adminSave, secret);

        ShopLoginDTO shopLogin = new ShopLoginDTO();
        shopLogin.setPassword(secret);
        shopLogin.setSmsCode(secret);
        shopLogin.setCaptchaCode(secret);
        assertSensitiveAbsent(shopLogin, secret);

        BalanceTransferDTO transfer = new BalanceTransferDTO();
        transfer.setPaymentPassword(secret);
        assertSensitiveAbsent(transfer, secret);

        ShopPasswordChangeDTO passwordChange = new ShopPasswordChangeDTO();
        passwordChange.setCurrentPassword(secret);
        passwordChange.setNewPassword(secret);
        passwordChange.setSmsCode(secret);
        assertSensitiveAbsent(passwordChange, secret);

        BalancePayDTO balancePay = new BalancePayDTO();
        balancePay.setPaymentPassword(secret);
        assertSensitiveAbsent(balancePay, secret);

        PaymentPasswordDTO paymentPassword = new PaymentPasswordDTO();
        paymentPassword.setOldPassword(secret);
        paymentPassword.setNewPassword(secret);
        paymentPassword.setLoginPassword(secret);
        paymentPassword.setSmsCode(secret);
        assertSensitiveAbsent(paymentPassword, secret);

        AdminAssetChangeDTO adminAssetChange = new AdminAssetChangeDTO();
        adminAssetChange.setAdminPassword(secret);
        assertSensitiveAbsent(adminAssetChange, secret);

        AdminMemberPhoneUpdateDTO phoneUpdate = new AdminMemberPhoneUpdateDTO();
        phoneUpdate.setAdminPassword(secret);
        assertSensitiveAbsent(phoneUpdate, secret);

        AdminMemberPasswordResetDTO passwordReset = new AdminMemberPasswordResetDTO();
        passwordReset.setNewPassword(secret);
        passwordReset.setAdminPassword(secret);
        assertSensitiveAbsent(passwordReset, secret);
    }

    private void assertSensitiveAbsent(Object value, String secret) {
        assertFalse(value.toString().contains(secret), value.getClass().getSimpleName() + " leaked a secret");
    }

    private DmsAdminSession adminSession(String storedTokenHash) {
        DmsAdminSession session = new DmsAdminSession();
        session.setToken(storedTokenHash);
        return session;
    }

    private DmsShopMemberSession memberSession(String storedTokenHash) {
        DmsShopMemberSession session = new DmsShopMemberSession();
        session.setToken(storedTokenHash);
        return session;
    }
}
