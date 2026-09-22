package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.dao.AdminDashboardDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.vo.AdminDashboardVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServicePermissionTest {

    @Mock private AdminDashboardDao dashboardDao;
    @Mock private AdminAuthService adminAuthService;
    @InjectMocks private AdminDashboardServiceImpl service;

    @AfterEach
    void clearAdminContext() {
        AdminContext.clear();
    }

    @Test
    void adminReadOnlyDoesNotQueryOrSerializeBusinessData() throws Exception {
        useAdmin("admin:read");

        AdminDashboardVO result = service.getDashboard();

        assertNull(result.getTotalSalesAmount());
        assertNull(result.getTotalReceiptAmount());
        assertNull(result.getRegisteredMemberCount());
        assertNull(result.getMemberRegionDistribution());
        assertNull(result.getProductRanking());
        assertNull(result.getLowStockProducts());
        assertNull(result.getUnsettledCommission());
        assertNull(result.getLatestCommissions());
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("totalSalesAmount"));
        assertFalse(json.contains("totalReceiptAmount"));
        assertFalse(json.contains("registeredMemberCount"));
        assertFalse(json.contains("memberRegionDistribution"));
        assertFalse(json.contains("productRanking"));
        assertFalse(json.contains("lowStockProducts"));
        assertFalse(json.contains("unsettledCommission"));
        assertFalse(json.contains("latestCommissions"));
        verifyNoInteractions(dashboardDao);
    }

    @Test
    void financePermissionOnlyLoadsFinanceGroup() {
        useAdmin("admin:read,finance:read");

        AdminDashboardVO result = service.getDashboard();

        assertNotNull(result.getTotalSalesAmount());
        assertNotNull(result.getTotalReceiptAmount());
        assertEquals(30, result.getPerformanceTrend().size());
        assertEquals(12, result.getMonthlyPerformanceTrend().size());
        assertNull(result.getRegisteredMemberCount());
        assertNull(result.getProductRanking());
        assertNull(result.getUnsettledCommission());
        verify(dashboardDao, never()).countMembers();
        verify(dashboardDao, never()).selectProductRanking(1L, 10);
        verify(dashboardDao, never()).sumUnsettledCommission(1L);
    }

    @Test
    void memberProductAndCommissionPermissionsStayInTheirOwnGroups() {
        useAdmin("admin:read,shop:member,shop:product,commission:manage");

        AdminDashboardVO result = service.getDashboard();

        assertNotNull(result.getRegisteredMemberCount());
        assertNotNull(result.getMemberRegionDistribution());
        assertNotNull(result.getProductRanking());
        assertNotNull(result.getLowStockProducts());
        assertNotNull(result.getUnsettledCommission());
        assertNotNull(result.getLatestCommissions());
        assertNull(result.getTotalSalesAmount());
        assertNull(result.getTotalReceiptAmount());
        verify(dashboardDao, never()).sumSales(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(dashboardDao, never()).selectFinanceSummary(1L);
    }

    private void useAdmin(String permissions) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9001L);
        admin.setPermissions(permissions);
        AdminContext.set(admin);
        Set<String> granted = Arrays.stream(permissions.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        when(adminAuthService.hasPermission(org.mockito.ArgumentMatchers.same(admin), anyString())).thenAnswer(invocation -> {
            String permission = invocation.getArgument(1);
            return granted.contains("*") || granted.contains(permission);
        });
    }
}
