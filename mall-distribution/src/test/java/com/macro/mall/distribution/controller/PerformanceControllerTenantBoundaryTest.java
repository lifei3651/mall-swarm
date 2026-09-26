package com.macro.mall.distribution.controller;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.DistributedScheduledTaskRunner;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.vo.PerformanceOverviewVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PerformanceControllerTenantBoundaryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 26);
    private final PerformanceService service = mock(PerformanceService.class);
    private final DmsTenantDao tenantDao = mock(DmsTenantDao.class);
    private final PerformanceController controller = new PerformanceController(
            service, mock(DistributedScheduledTaskRunner.class), tenantDao);

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void everyPerformanceReadFailsClosedBeforeAnyGlobalQueryWhenTwoCustomersShareDatabase() {
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L, 1), tenant(2L, 1)));

        assertBlocked(() -> controller.getPerformanceOverview("agent", DATE, DATE));
        assertBlocked(() -> controller.getSubordinateContributions("agent", DATE, DATE));
        assertBlocked(() -> controller.getSubordinateOrderDetails("agent", 22L, DATE, DATE));
        assertBlocked(() -> controller.getPerformanceSourceDetails("agent", DATE, DATE));
        assertBlocked(() -> controller.getPerformanceRanking(3, 3, DATE, 1, 20));

        verifyNoInteractions(service);
        // The ranking guard runs before PageHelper can leave a page request on the thread.
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void disabledCustomersHistoryAlsoPreventsGlobalPerformanceDisclosure() {
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L, 1), tenant(2L, 0)));

        assertBlocked(() -> controller.getPerformanceRanking(2, 3, DATE, 1, 20));
        verifyNoInteractions(service);
    }

    @Test
    void singleCustomerRetainsEveryExistingReadPath() {
        when(tenantDao.selectAll()).thenReturn(List.of(tenant(1L, 1)));
        when(service.resolveAgentId("agent")).thenReturn(11L);
        when(service.getPerformanceOverview(11L, DATE, DATE)).thenReturn(new PerformanceOverviewVO());
        when(service.getSubordinateContributions(11L, DATE, DATE)).thenReturn(List.of());
        when(service.getSubordinateOrderDetails(11L, 22L, DATE, DATE)).thenReturn(List.of());
        when(service.getPerformanceSourceDetails(11L, DATE, DATE)).thenReturn(List.of());
        when(service.getPerformanceRanking(3, 3, DATE)).thenReturn(List.of());

        assertDoesNotThrow(() -> controller.getPerformanceOverview("agent", DATE, DATE));
        assertDoesNotThrow(() -> controller.getSubordinateContributions("agent", DATE, DATE));
        assertDoesNotThrow(() -> controller.getSubordinateOrderDetails("agent", 22L, DATE, DATE));
        assertDoesNotThrow(() -> controller.getPerformanceSourceDetails("agent", DATE, DATE));
        assertDoesNotThrow(() -> controller.getPerformanceRanking(3, 3, DATE, 1, 20));

        verify(service).getPerformanceOverview(11L, DATE, DATE);
        verify(service).getSubordinateContributions(11L, DATE, DATE);
        verify(service).getSubordinateOrderDetails(11L, 22L, DATE, DATE);
        verify(service).getPerformanceSourceDetails(11L, DATE, DATE);
        verify(service).getPerformanceRanking(3, 3, DATE);
    }

    private void assertBlocked(Runnable read) {
        ApiException error = assertThrows(ApiException.class, read::run);
        assertEquals("多客户模式下业绩视图待完成客户隔离", error.getMessage());
    }

    private DmsTenant tenant(Long id, Integer status) {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(id);
        tenant.setStatus(status);
        return tenant;
    }
}
