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
        when(adminUserDao.selectByUsername("operator")).thenReturn(admin);

        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator");
        dto.setPassword(password);
        dto.setCaptchaId("captcha-id");
        dto.setCaptchaCode("8A2K");
        LocalDateTime expectedExpireAfter = LocalDateTime.now().plusHours(12);
        var result = service.login(dto);

        verify(captchaService).verify("admin", "captcha-id", "8A2K");
        verify(adminSessionDao).disableByAdminId(9L);

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(adminUserDao).updatePassword(eq(9L), passwordHash.capture(), eq("BCRYPT"));
        assertTrue(BCrypt.checkpw(password, passwordHash.getValue()));

        ArgumentCaptor<DmsAdminSession> session = ArgumentCaptor.forClass(DmsAdminSession.class);
        verify(adminSessionDao).insert(session.capture());
        assertNotEquals(result.getToken(), session.getValue().getToken());
        assertEquals(SecureUtil.sha256(result.getToken()), session.getValue().getToken());
        assertFalse(session.getValue().getExpireTime().isBefore(expectedExpireAfter));
        assertFalse(session.getValue().getExpireTime().isAfter(LocalDateTime.now().plusHours(12)));
        assertEquals(session.getValue().getExpireTime(), result.getExpireTime());
    }

    @Test
    void adminLoginStopsBeforePasswordLookupWhenCaptchaFails() {
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator");
        dto.setPassword("password");
        dto.setCaptchaId("expired-captcha");
        dto.setCaptchaCode("0000");
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
        when(adminUserDao.selectByUsername("operator")).thenReturn(admin);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator"); dto.setPassword("wrong-password");
        dto.setCaptchaId("captcha"); dto.setCaptchaCode("8A2K");

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
        when(adminUserDao.selectByUsername("operator")).thenReturn(admin);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(adminUserDao, adminSessionDao, captchaService);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setUsername("operator"); dto.setPassword(password);
        dto.setCaptchaId("captcha"); dto.setCaptchaCode("8A2K");

        service.login(dto);

        verify(adminUserDao).clearLoginLock(9L);
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
                agentService, captchaService, smsVerificationService);
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount(member.getPhone());
        dto.setPassword(password);
        dto.setLoginType("password");
        var result = service.login(dto);

        verify(memberSessionDao).disableByMemberId(12L);
        ArgumentCaptor<DmsShopMemberSession> session = ArgumentCaptor.forClass(DmsShopMemberSession.class);
        verify(memberSessionDao).insert(session.capture());
        assertNotEquals(result.getToken(), session.getValue().getToken());
        assertEquals(SecureUtil.sha256(result.getToken()), session.getValue().getToken());
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
}
