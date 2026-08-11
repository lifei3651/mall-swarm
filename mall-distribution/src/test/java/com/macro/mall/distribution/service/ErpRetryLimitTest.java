package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.dao.DmsErpSyncTaskDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.erp.ErpAdapter;
import com.macro.mall.distribution.service.impl.ErpIntegrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErpRetryLimitTest {

    @Mock private DmsErpIntegrationDao integrationDao;
    @Mock private DmsErpSyncTaskDao taskDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private ErpAdapter adapter;
    @Mock private OperationLogService operationLogService;
    @Mock private OrderShipmentService orderShipmentService;
    private ErpIntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ErpIntegrationServiceImpl(integrationDao, taskDao, orderDao, List.of(adapter),
                operationLogService, orderShipmentService);
        ReflectionTestUtils.setField(service, "maxAutoRetries", 3);
    }

    @Test
    void automaticScanPassesConfiguredRetryLimitToDatabase() {
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(20), eq(3))).thenReturn(List.of());

        service.retryPendingTasks(20);

        verify(taskDao).stopExceededRetries(3);
        verify(taskDao).selectRetryable(any(LocalDateTime.class), eq(20), eq(3));
    }

    @Test
    void thirdFailureStopsAutomaticRetryAndClearsNextRetryTime() {
        DmsErpSyncTask task = new DmsErpSyncTask();
        task.setId(11L);
        task.setIntegrationId(22L);
        task.setBizId("33");
        task.setRetryCount(2);
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setProviderCode("TEST_ERP");
        DmsShopOrder order = new DmsShopOrder();
        when(taskDao.selectById(11L)).thenReturn(task);
        when(integrationDao.selectById(22L)).thenReturn(integration);
        when(orderDao.selectById(33L)).thenReturn(order);
        when(adapter.providerCode()).thenReturn("TEST_ERP");
        when(adapter.pushOrder(integration, order)).thenReturn(ErpAdapter.ErpPushResult.failed("ERP不可用"));

        assertFalse(service.retryTask(11L));

        verify(taskDao).markFailure(eq(11L), eq(3), eq(3), isNull(), contains("达到自动重试上限"));
        verify(operationLogService).log(eq("ERP"), eq("ORDER_PUSH_STOPPED"), eq("ERP_SYNC_TASK"),
                eq("11"), isNull(), contains("ERP不可用"), contains("已停止自动重试"));
    }
}
