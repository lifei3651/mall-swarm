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
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void clearTenantContext() { TenantContext.clear(); }

    @Test
    void automaticScanPassesConfiguredRetryLimitToDatabase() {
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(20), eq(3))).thenReturn(List.of());

        service.retryPendingTasks(20);

        verify(taskDao).stopExceededRetries(3);
        verify(taskDao).selectRetryable(any(LocalDateTime.class), eq(20), eq(3));
    }

    @Test
    void adminReadsDefaultToCurrentTenantAndRejectExplicitOtherTenant() {
        TenantContext.setTenantId(2L);
        when(integrationDao.selectList(2L)).thenReturn(List.of());

        assertTrue(service.listIntegrations(null).isEmpty());
        assertTrue(service.listIntegrations(2L).isEmpty());
        assertThrows(RuntimeException.class, () -> service.listIntegrations(3L));
        assertTrue(service.listTasks(null, null).isEmpty());

        verify(integrationDao, times(2)).selectList(2L);
        verify(integrationDao, never()).selectList(3L);
        verify(taskDao).selectList(2L, null, null);
    }

    @Test
    void adminCannotSaveOtherTenantIntegrationButMissingTenantUsesCurrentTenant() {
        TenantContext.setTenantId(2L);
        DmsErpIntegration otherTenant = integration("JUSHUITAN");
        otherTenant.setTenantId(3L);
        assertThrows(RuntimeException.class, () -> service.saveIntegration(otherTenant));
        verifyNoInteractions(integrationDao);

        DmsErpIntegration own = integration("JUSHUITAN");
        own.setTenantId(null);
        own.setEnabled(0);
        DmsErpIntegration stored = integration("JUSHUITAN");
        stored.setTenantId(2L);
        stored.setId(42L);
        when(integrationDao.selectByTenantAndProvider(2L, "JUSHUITAN")).thenReturn(null, stored);

        assertEquals(42L, service.saveIntegration(own).getId());

        assertEquals(2L, own.getTenantId());
        verify(integrationDao).insert(same(own));
        verify(integrationDao, never()).update(any());
    }

    @Test
    void adminManualRetrySucceedsForOwnTenantTask() {
        TenantContext.setTenantId(2L);
        DmsErpSyncTask task = retryTask(11L, 22L, "33");
        task.setTenantId(2L);
        DmsErpIntegration integration = integration("TEST_ERP");
        integration.setTenantId(2L);
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(2L);
        when(taskDao.selectById(2L, 11L)).thenReturn(task);
        when(integrationDao.selectById(22L)).thenReturn(integration);
        when(orderDao.selectById(33L)).thenReturn(order);
        when(adapter.providerCode()).thenReturn("TEST_ERP");
        when(adapter.pushOrder(integration, order)).thenReturn(new ErpAdapter.ErpPushResult(true, "ok"));

        assertTrue(service.retryTask(11L));

        verify(taskDao).markSuccess(11L, "ok");
        assertEquals(2L, TenantContext.getCurrentTenantId());
    }

    @Test
    void oneAdapterExceptionDoesNotStopLaterRetryTasks() {
        DmsErpSyncTask first = retryTask(11L, 101L, "201");
        DmsErpSyncTask second = retryTask(12L, 102L, "202");
        DmsErpIntegration firstIntegration = integration("TEST_ERP");
        DmsErpIntegration secondIntegration = integration("TEST_ERP");
        DmsShopOrder firstOrder = new DmsShopOrder();
        firstOrder.setTenantId(1L);
        DmsShopOrder secondOrder = new DmsShopOrder();
        secondOrder.setTenantId(1L);
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
    void automaticRetryReadsOrderInTaskTenantAndRestoresPreviousContext() {
        DmsErpSyncTask task = retryTask(11L, 22L, "33");
        task.setTenantId(2L);
        DmsErpIntegration integration = integration("TEST_ERP");
        integration.setTenantId(2L);
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(2L);
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(1), eq(3))).thenReturn(List.of(task));
        when(integrationDao.selectById(22L)).thenReturn(integration);
        doCallRealMethod().when(orderDao).selectById(33L);
        when(orderDao.selectByIdScoped(2L, 33L)).thenReturn(order);
        when(adapter.providerCode()).thenReturn("TEST_ERP");
        when(adapter.pushOrder(integration, order)).thenAnswer(invocation -> {
            assertEquals(2L, TenantContext.getCurrentTenantId());
            return new ErpAdapter.ErpPushResult(true, "ok");
        });
        TenantContext.setTenantId(9L);

        assertEquals(1, service.retryPendingTasks(1));

        verify(orderDao).selectByIdScoped(2L, 33L);
        verify(taskDao).markSuccess(11L, "ok");
        assertEquals(9L, TenantContext.getCurrentTenantId());
    }

    @Test
    void automaticRetryRejectsTenantMismatchAndContinuesWithNextTenant() {
        DmsErpSyncTask mismatched = retryTask(11L, 101L, "201");
        mismatched.setTenantId(2L);
        DmsErpSyncTask valid = retryTask(12L, 102L, "202");
        valid.setTenantId(3L);
        DmsErpIntegration wrongIntegration = integration("TEST_ERP");
        wrongIntegration.setTenantId(3L);
        DmsErpIntegration validIntegration = integration("TEST_ERP");
        validIntegration.setTenantId(3L);
        DmsShopOrder firstOrder = new DmsShopOrder();
        firstOrder.setTenantId(2L);
        DmsShopOrder secondOrder = new DmsShopOrder();
        secondOrder.setTenantId(3L);
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(2), eq(3))).thenReturn(List.of(mismatched, valid));
        when(integrationDao.selectById(101L)).thenReturn(wrongIntegration);
        when(integrationDao.selectById(102L)).thenReturn(validIntegration);
        doCallRealMethod().when(orderDao).selectById(anyLong());
        when(orderDao.selectByIdScoped(2L, 201L)).thenReturn(firstOrder);
        when(orderDao.selectByIdScoped(3L, 202L)).thenReturn(secondOrder);
        when(adapter.providerCode()).thenReturn("TEST_ERP");
        when(adapter.pushOrder(validIntegration, secondOrder)).thenAnswer(invocation -> {
            assertEquals(3L, TenantContext.getCurrentTenantId());
            return new ErpAdapter.ErpPushResult(true, "ok");
        });

        assertEquals(2, service.retryPendingTasks(2));

        verify(taskDao).markFailure(eq(11L), eq(2), eq(1), any(LocalDateTime.class), contains("租户不一致"));
        verify(taskDao).markSuccess(12L, "ok");
        verify(adapter, times(1)).pushOrder(any(), any());
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void manualRetryKeepsCallerTenantInsteadOfAdoptingTaskTenant() {
        TenantContext.setTenantId(1L);

        assertThrows(RuntimeException.class, () -> service.retryTask(11L));

        verify(taskDao).selectById(1L, 11L);
        verify(taskDao, never()).markFailure(anyLong(), anyInt(), anyInt(), any(), anyString());
        verifyNoInteractions(integrationDao, orderDao, adapter, operationLogService);
        assertEquals(1L, TenantContext.getCurrentTenantId());
    }

    @Test
    void thirdFailureStopsAutomaticRetryAndClearsNextRetryTime() {
        DmsErpSyncTask task = new DmsErpSyncTask();
        task.setId(11L);
        task.setIntegrationId(22L);
        task.setTenantId(1L);
        task.setBizId("33");
        task.setRetryCount(2);
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setProviderCode("TEST_ERP");
        integration.setEnabled(1);
        integration.setTenantId(1L);
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(1L);
        when(taskDao.selectById(1L, 11L)).thenReturn(task);
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

    @Test
    void disabledIntegrationDoesNotPushPreviouslyQueuedTaskOrConsumeRetry() {
        DmsErpSyncTask task = retryTask(11L, 22L, "33");
        DmsErpIntegration integration = integration("TEST_ERP");
        integration.setEnabled(0);
        when(taskDao.selectById(1L, 11L)).thenReturn(task);
        when(integrationDao.selectById(22L)).thenReturn(integration);

        assertFalse(service.retryTask(11L));

        verifyNoInteractions(orderDao, adapter, operationLogService);
        verify(taskDao, never()).markFailure(anyLong(), anyInt(), anyInt(), any(), anyString());
        verify(taskDao, never()).markSuccess(anyLong(), anyString());
    }

    @Test
    void automaticRetryAlsoChecksDisabledStateAfterQueueSelection() {
        DmsErpSyncTask task = retryTask(11L, 22L, "33");
        DmsErpIntegration integration = integration("TEST_ERP");
        integration.setEnabled(0);
        when(taskDao.selectRetryable(any(LocalDateTime.class), eq(1), eq(3))).thenReturn(List.of(task));
        when(integrationDao.selectById(22L)).thenReturn(integration);

        assertEquals(1, service.retryPendingTasks(1));

        verifyNoInteractions(orderDao, adapter, operationLogService);
        verify(taskDao, never()).markFailure(anyLong(), anyInt(), anyInt(), any(), anyString());
        verify(taskDao, never()).markSuccess(anyLong(), anyString());
    }

    @Test
    void taskFromAnotherTenantNeverReachesTheExternalAdapter() {
        DmsErpSyncTask task = retryTask(11L, 22L, "33");
        DmsErpIntegration integration = integration("TEST_ERP");
        integration.setTenantId(2L);
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(1L);
        when(taskDao.selectById(1L, 11L)).thenReturn(task);
        when(integrationDao.selectById(22L)).thenReturn(integration);
        when(orderDao.selectById(33L)).thenReturn(order);

        assertFalse(service.retryTask(11L));

        verifyNoInteractions(adapter);
        verify(taskDao).markFailure(eq(11L), eq(2), eq(1), any(LocalDateTime.class), contains("租户不一致"));
    }

    private DmsErpSyncTask retryTask(Long id, Long integrationId, String orderId) {
        DmsErpSyncTask task = new DmsErpSyncTask();
        task.setId(id);
        task.setIntegrationId(integrationId);
        task.setTenantId(1L);
        task.setBizId(orderId);
        task.setRetryCount(0);
        return task;
    }

    private DmsErpIntegration integration(String providerCode) {
        DmsErpIntegration integration = new DmsErpIntegration();
        integration.setProviderCode(providerCode);
        integration.setEnabled(1);
        integration.setTenantId(1L);
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
