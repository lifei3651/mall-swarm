package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminSelfPasswordDTO;
import com.macro.mall.distribution.dto.AdminTemporaryCredentialDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdminUserServiceSecurityTest {

    private final DmsAdminUserDao userDao = mock(DmsAdminUserDao.class);
    private final DmsAdminSessionDao sessionDao = mock(DmsAdminSessionDao.class);
    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final DmsMerchantDao merchantDao = mock(DmsMerchantDao.class);
    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(
            userDao, sessionDao, authService, merchantDao, operationLogService);

    @AfterEach
    void clearContext() { AdminContext.clear(); }

    @Test
    void delegatedAdminCannotGrantSuperAdministratorPermission() {
        DmsAdminUser actor = admin(10L, "system:manage,admin:read");
        AdminContext.set(actor);
        when(authService.permissions(actor)).thenReturn(List.of("system:manage", "admin:read"));
        AdminUserSaveDTO dto = new AdminUserSaveDTO();
        dto.setUsername("newadmin");
        dto.setPassword("Password-123");
        dto.setCurrentAdminPassword("Current-123");
        dto.setPermissions(List.of("*"));

        assertThrows(ApiException.class, () -> service.saveUser(dto));
        verify(authService).verifyPassword(actor, "Current-123");
        verify(userDao, never()).insert(any());
    }

    @Test
    void delegatedAdminCannotResetRootPassword() {
        DmsAdminUser actor = admin(10L, "system:manage,admin:read");
        DmsAdminUser root = admin(1L, "*");
        AdminContext.set(actor);
        when(userDao.selectById(1L)).thenReturn(root);
        when(authService.permissions(actor)).thenReturn(List.of("system:manage", "admin:read"));
        when(authService.permissions(root)).thenReturn(List.of("*"));
        AdminPasswordDTO dto = new AdminPasswordDTO();
        dto.setPassword("New-password-123");
        dto.setCurrentAdminPassword("Current-123");

        assertThrows(ApiException.class, () -> service.updatePassword(1L, dto));
        verify(userDao, never()).updatePassword(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void suspendedMerchantAccountKeepsOrderAndAfterSaleWorkspacePermissions() {
        DmsAdminUser actor = admin(1L, "*");
        DmsAdminUser merchantUser = admin(20L, "admin:read,shop:product");
        merchantUser.setMerchantId(10001L);
        DmsMerchant merchant = new DmsMerchant();
        merchant.setId(10001L);
        merchant.setStatus(0);
        merchant.setBusinessStatus("SUSPENDED");
        merchant.setExitStatus("NORMAL");
        AdminContext.set(actor);
        when(authService.permissions(actor)).thenReturn(List.of("*"));
        when(authService.permissions(merchantUser)).thenAnswer(invocation -> List.of(merchantUser.getPermissions().split(",")));
        when(userDao.selectById(20L)).thenReturn(merchantUser);
        when(merchantDao.selectById(10001L)).thenReturn(merchant);

        AdminUserSaveDTO dto = new AdminUserSaveDTO();
        dto.setId(20L);
        dto.setUsername("merchant-a");
        dto.setNickname("商户A");
        dto.setMerchantId(10001L);
        dto.setStatus(1);
        dto.setCurrentAdminPassword("Current-123");
        dto.setPermissions(List.of("admin:read", "shop:product", "shop:order", "shop:aftersale", "finance:read", "finance:manage"));

        assertDoesNotThrow(() -> service.saveUser(dto));
        verify(userDao).update(argThat(user -> user.getMerchantId().equals(10001L)
                && user.getPermissions().contains("shop:order")
                && user.getPermissions().contains("shop:aftersale")));
    }

    @Test
    void forcedPasswordChangeClearsFlagAndRevokesExistingSessions() {
        DmsAdminUser actor = admin(10L, "admin:read");
        actor.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("Old-password-123"));
        actor.setSalt("BCRYPT");
        actor.setMustChangePassword(1);
        AdminContext.set(actor);
        when(userDao.selectById(10L)).thenReturn(actor);
        when(userDao.updatePassword(eq(10L), anyString(), eq("BCRYPT"), eq(0))).thenReturn(1);
        AdminSelfPasswordDTO dto = new AdminSelfPasswordDTO();
        dto.setCurrentPassword("Old-password-123");
        dto.setNewPassword("New-password-456!");

        assertTrue(service.changeOwnPassword(dto));

        verify(authService).verifyPassword(actor, "Old-password-123");
        verify(userDao).updatePassword(eq(10L), anyString(), eq("BCRYPT"), eq(0));
        verify(sessionDao).disableByAdminId(10L);
    }

    @Test
    void ownPasswordCannotReuseCurrentPassword() {
        DmsAdminUser actor = admin(10L, "admin:read");
        actor.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("Same-password-123!"));
        actor.setSalt("BCRYPT");
        AdminContext.set(actor);
        when(userDao.selectById(10L)).thenReturn(actor);
        AdminSelfPasswordDTO dto = new AdminSelfPasswordDTO();
        dto.setCurrentPassword("Same-password-123!");
        dto.setNewPassword("Same-password-123!");

        assertThrows(ApiException.class, () -> service.changeOwnPassword(dto));
        verify(userDao, never()).updatePassword(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void platformCreatesAccountWithOneTimeGeneratedCredential() {
        DmsAdminUser actor = admin(1L, "*");
        AdminContext.set(actor);
        when(authService.permissions(actor)).thenReturn(List.of("*"));
        AtomicReference<DmsAdminUser> inserted = new AtomicReference<>();
        AtomicReference<String> insertedPasswordHash = new AtomicReference<>();
        doAnswer(invocation -> {
            DmsAdminUser value = invocation.getArgument(0);
            value.setId(20L);
            inserted.set(value);
            insertedPasswordHash.set(value.getPasswordHash());
            return 1;
        }).when(userDao).insert(any(DmsAdminUser.class));
        when(userDao.selectById(20L)).thenAnswer(invocation -> inserted.get());
        AdminUserSaveDTO dto = new AdminUserSaveDTO();
        dto.setUsername("new_operator");
        dto.setNickname("新运营");
        dto.setCurrentAdminPassword("Current-123");
        dto.setPermissions(List.of("admin:read"));

        DmsAdminUser saved = service.saveUser(dto);

        assertNotNull(saved.getTemporaryPassword());
        assertTrue(saved.getTemporaryPassword().matches("(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{16}"));
        assertEquals(1, saved.getMustChangePassword());
        assertNotNull(saved.getCredentialExpiresAt());
        assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw(saved.getTemporaryPassword(), insertedPasswordHash.get()));
    }

    @Test
    void merchantOwnerIssuesCredentialOnlyForOwnStaffAccount() {
        DmsAdminUser actor = admin(100L, "admin:read,merchant:staff-manage");
        actor.setMerchantId(8001L);
        DmsAdminUser ownStaff = admin(101L, "admin:read,shop:order");
        ownStaff.setMerchantId(8001L);
        ownStaff.setRoleCode("MERCHANT_STAFF");
        DmsAdminUser otherMerchantStaff = admin(102L, "admin:read,shop:order");
        otherMerchantStaff.setMerchantId(8002L);
        otherMerchantStaff.setRoleCode("MERCHANT_STAFF");
        AdminContext.set(actor);
        when(authService.permissions(ownStaff)).thenReturn(List.of("admin:read", "shop:order"));
        when(userDao.selectById(101L)).thenReturn(ownStaff);
        when(userDao.selectById(102L)).thenReturn(otherMerchantStaff);
        when(userDao.updateTemporaryPassword(eq(101L), anyString(), eq("BCRYPT"), any())).thenReturn(1);
        AdminTemporaryCredentialDTO dto = new AdminTemporaryCredentialDTO();
        dto.setCurrentAdminPassword("Current-123");

        var credential = service.issueTemporaryCredential(101L, dto);

        assertEquals("admin101", credential.getUsername());
        assertNotNull(credential.getTemporaryPassword());
        verify(sessionDao).disableByAdminId(101L);
        assertThrows(ApiException.class, () -> service.issueTemporaryCredential(102L, dto));
        verify(userDao, never()).updateTemporaryPassword(eq(102L), anyString(), anyString(), any());
    }

    @Test
    void merchantStaffRoleAddsReadDependenciesForAfterSaleAndFinanceManagement() {
        DmsAdminUser actor = admin(100L, "admin:read,shop:order,shop:aftersale,finance:read,finance:manage,merchant:staff-manage");
        actor.setMerchantId(8001L);
        DmsMerchant merchant = new DmsMerchant();
        merchant.setId(8001L);
        merchant.setExitStatus("NORMAL");
        AtomicReference<DmsAdminUser> inserted = new AtomicReference<>();
        AdminContext.set(actor);
        when(authService.permissions(actor)).thenReturn(List.of("admin:read", "shop:order", "shop:aftersale", "finance:read", "finance:manage", "merchant:staff-manage"));
        when(merchantDao.selectById(8001L)).thenReturn(merchant);
        doAnswer(invocation -> {
            DmsAdminUser value = invocation.getArgument(0);
            value.setId(103L);
            inserted.set(value);
            return 1;
        }).when(userDao).insert(any(DmsAdminUser.class));
        when(userDao.selectById(103L)).thenAnswer(invocation -> inserted.get());
        AdminUserSaveDTO dto = new AdminUserSaveDTO();
        dto.setUsername("after_sale_finance_staff");
        dto.setNickname("售后财务岗");
        dto.setCurrentAdminPassword("Current-123");
        dto.setPermissions(List.of("admin:read", "shop:aftersale", "finance:manage"));

        DmsAdminUser saved = service.saveUser(dto);

        assertTrue(saved.getPermissions().contains("shop:order"));
        assertTrue(saved.getPermissions().contains("finance:read"));
        assertEquals("MERCHANT_STAFF", saved.getRoleCode());
        assertEquals(8001L, saved.getMerchantId());
    }

    private DmsAdminUser admin(Long id, String permissions) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername("admin" + id);
        admin.setPermissions(permissions);
        admin.setStatus(1);
        return admin;
    }
}
