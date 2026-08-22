package com.macro.mall.distribution.config;

import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminInitialPasswordGateTest {

    private final AdminAuthService authService = mock(AdminAuthService.class);
    private final AdminSecurityConfig.AdminSecurityInterceptor interceptor =
            new AdminSecurityConfig.AdminSecurityInterceptor(authService, mock(OperationLogService.class));

    @AfterEach
    void clearContext() {
        AdminContext.clear();
    }

    @Test
    void forcedAccountCannotEnterBusinessApiBeforeChangingPassword() throws Exception {
        DmsAdminUser admin = forcedAdmin();
        when(authService.requireAdmin(null)).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/distribution/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("必须先修改后台初始密码"));
    }

    @Test
    void forcedAccountCanUseOwnPasswordEndpoint() throws Exception {
        DmsAdminUser admin = forcedAdmin();
        when(authService.requireAdmin(null)).thenReturn(admin);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/distribution/admin-auth/password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(admin, AdminContext.get());
    }

    private DmsAdminUser forcedAdmin() {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9L);
        admin.setStatus(1);
        admin.setPermissions("admin:read");
        admin.setMustChangePassword(1);
        return admin;
    }
}
