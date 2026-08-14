package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAdminSessionDao;
import com.macro.mall.distribution.dao.DmsAdminUserDao;
import com.macro.mall.distribution.dto.AdminPasswordDTO;
import com.macro.mall.distribution.dto.AdminUserSaveDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminUserServiceSecurityTest {

    private final DmsAdminUserDao userDao = mock(DmsAdminUserDao.class);
    private final DmsAdminSessionDao sessionDao = mock(DmsAdminSessionDao.class);
    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(userDao, sessionDao, authService);

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

    private DmsAdminUser admin(Long id, String permissions) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername("admin" + id);
        admin.setPermissions(permissions);
        admin.setStatus(1);
        return admin;
    }
}
