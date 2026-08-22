package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.dao.DmsErpSyncTaskDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.erp.ErpAdapter;
import com.macro.mall.distribution.erp.JushuitanErpAdapter;
import com.macro.mall.distribution.service.impl.ErpIntegrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
    void oneAdapterExceptionDoesNotStopLaterRetryTasks() {
        DmsErpSyncTask first = retryTask(11L, 101L, "201");
        DmsErpSyncTask second = retryTask(12L, 102L, "202");
        DmsErpIntegration firstIntegration = integration("TEST_ERP");
        DmsErpIntegration secondIntegration = integration("TEST_ERP");
        DmsShopOrder firstOrder = new DmsShopOrder();
        DmsShopOrder secondOrder = new DmsShopOrder();
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(20), eq(3))).thenReturn(List.of(first, second));
        when(integrationDao.selectById(101L)).thenReturn(firstIntegration);
        when(integrationDao.selectById(102L)).thenReturn(secondIntegration);
        when(orderDao.selectById(201L)).thenReturn(firstOrder);
        when(orderDao.selectById(202L)).thenReturn(secondOrder);
        when(adapter.providerCode()).thenReturn("TEST_ERP");
        when(adapter.pushOrder(same(firstIntegration), same(firstOrder))).thenThrow(new IllegalStateException("vendor timeout"));
        when(adapter.pushOrder(same(secondIntegration), same(secondOrder))).thenReturn(new ErpAdapter.ErpPushResult(true, "ok"));

        assertEquals(2, service.retryPendingTasks(20));

        verify(taskDao).markFailure(eq(11L), eq(2), eq(1), any(LocalDateTime.class), eq("ERP适配器调用异常"));
        verify(taskDao).markSuccess(12L, "ok");
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

    @Test
    void callbackUsesAuthenticatedTenantAndRestoresPreviousContext() {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setTenantId(2L);
        integration.setProviderCode("JUSHUITAN");
        integration.setCallbackToken("tenant-2-callback-token-1234567890");
        integration.setEnabled(1);
        ErpShipmentCallbackDTO callback = shipmentCallback(2L, "tenant-2-callback-token-1234567890");
        when(integrationDao.selectByTenantAndProvider(2L, "JUSHUITAN")).thenReturn(integration);
        when(orderShipmentService.shipErpOrder("ORDER-2", "顺丰速运", "SF20260819001", 1, "JUSHUITAN"))
                .thenAnswer(ignored -> {
                    assertEquals(2L, TenantContext.getTenantId());
                    return true;
                });

        TenantContext.setTenantId(9L);
        try {
            assertTrue(service.receiveShipment(callback));
            assertEquals(9L, TenantContext.getTenantId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void callbackRejectsWrongTenantTokenBeforeShipment() {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setTenantId(2L);
        integration.setProviderCode("JUSHUITAN");
        integration.setCallbackToken("expected-token-12345678901234567890");
        integration.setEnabled(1);
        when(integrationDao.selectByTenantAndProvider(2L, "JUSHUITAN")).thenReturn(integration);

        assertThrows(RuntimeException.class, () -> service.receiveShipment(shipmentCallback(2L, "wrong-token")));
        verifyNoInteractions(orderShipmentService);
    }

    @Test
    void unfinishedVendorAdapterCannotBePresentedAsEnabled() {
        ErpIntegrationServiceImpl guardedService = new ErpIntegrationServiceImpl(
                integrationDao, taskDao, orderDao, List.of(new JushuitanErpAdapter()),
                operationLogService, orderShipmentService);
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setTenantId(1L);
        integration.setProviderCode("JUSHUITAN");
        integration.setEndpoint("https://erp.example.test/api");
        integration.setCallbackToken("callback-token-12345678901234567890");
        integration.setEnabled(1);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> guardedService.saveIntegration(integration));

        assertTrue(error.getMessage().contains("尚未完成客户授权接口映射"));
        verify(integrationDao, never()).insert(any());
        verify(integrationDao, never()).update(any());
    }

    private DmsErpSyncTask retryTask(Long id, Long integrationId, String orderId) {
        DmsErpSyncTask task = new DmsErpSyncTask();
        task.setId(id);
        task.setIntegrationId(integrationId);
        task.setBizId(orderId);
        task.setRetryCount(0);
        return task;
    }

    private DmsErpIntegration integration(String providerCode) {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setProviderCode(providerCode);
        return integration;
    }

    private ErpShipmentCallbackDTO shipmentCallback(Long tenantId, String token) {
        ErpShipmentCallbackDTO callback = new ErpShipmentCallbackDTO();
        callback.setTenantId(tenantId);
        callback.setProviderCode("JUSHUITAN");
        callback.setToken(token);
        callback.setOrderNo("ORDER-2");
        callback.setDeliveryCompany("顺丰速运");
        callback.setDeliveryNo("SF20260819001");
        callback.setShipmentQuantity(1);
        return callback;
    }
}
