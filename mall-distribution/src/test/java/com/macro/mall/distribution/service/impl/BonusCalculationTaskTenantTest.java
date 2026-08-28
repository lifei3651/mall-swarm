package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonusCalculationTaskTenantTest {

    @Mock private DmsBonusCalculationTaskDao taskDao;
    @Mock private DmsCommissionRecordDao commissionRecordDao;
    @Mock private DmsOrderPvDetailDao orderPvDetailDao;
    @Mock private DmsOrderFinanceDao orderFinanceDao;
    @Mock private DmsOrderRelationSnapshotDao relationSnapshotDao;
    @Mock private DmsBonusCalculationSnapshotDao snapshotDao;
    @Mock private DmsShopMemberDao shopMemberDao;
    @Mock private CommissionService commissionService;
    @Mock private DistributionAuditService auditService;
    @Mock private OrderBalanceAllocationService orderBalanceAllocationService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private BonusCalculationTaskServiceImpl service;

    @Test
    void retryUsesTaskTenantInsteadOfDefaultTenant() throws Exception {
        DmsBonusCalculationTask task = new DmsBonusCalculationTask();
        task.setId(70L);
        task.setTenantId(7L);
        task.setRuleVersionId(12L);
        task.setOrderId(500L);
        task.setOrderNo("ORDER-500");
        task.setOrderAmount(new BigDecimal("299.00"));
        task.setOrderUserId(1008L);
        task.setOrderUserName("客户会员");
        task.setStatus(0);
        task.setRetryCount(0);
        task.setMaxRetryCount(3);

        when(taskDao.selectById(70L)).thenReturn(task);
        when(taskDao.markProcessing(70L)).thenReturn(1);
        when(taskDao.markSuccess(70L)).thenReturn(1);
        when(commissionRecordDao.selectByOrderId(500L)).thenReturn(List.of());
        when(orderPvDetailDao.selectByOrderId(500L)).thenReturn(List.of());
        when(relationSnapshotDao.selectByOrderId(500L)).thenReturn(List.of());
        when(snapshotDao.selectByOrderId(500L)).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertTrue(service.processTask(70L));

        verify(commissionService).calculateAndRecordCommission(
                7L, 500L, "ORDER-500", new BigDecimal("299.00"), 1008L, "客户会员");
        verify(commissionService, never()).calculateAndRecordCommission(
                eq(500L), anyString(), any(BigDecimal.class), anyLong(), anyString());
    }
}
