package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        verify(userDao, never()).updatePassword(anyLong(), anyString(), anyString());
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

    private DmsAdminUser admin(Long id, String permissions) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername("admin" + id);
        admin.setPermissions(permissions);
        admin.setStatus(1);
        return admin;
    }
}
