package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsBonusCalculationSnapshotDao;
import com.macro.mall.distribution.dao.DmsBonusCalculationTaskDao;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsBonusCalculationSnapshot;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.vo.CommissionRecordVO;
import com.macro.mall.distribution.vo.OrderBonusTraceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderBonusTraceServiceTest {

    @Mock private DmsBonusCalculationTaskDao taskDao;
    @Mock private DmsBonusCalculationSnapshotDao calculationSnapshotDao;
    @Mock private DmsOrderRelationSnapshotDao relationSnapshotDao;
    @Mock private DmsCommissionRuleVersionDao ruleVersionDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsMemberAssetFlowDao assetFlowDao;
    @InjectMocks private OrderBonusTraceService traceService;

    @Test
    void keepsActualRecordsInsideFullTraceAndLinksSettlementAndRefundClawback() {
        LocalDateTime paidTime = LocalDateTime.of(2026, 8, 30, 10, 0);
        DmsShopOrder order = order(10L, paidTime, 3);
        DmsOrderRelationSnapshot relation = new DmsOrderRelationSnapshot();
        relation.setOrderId(10L);
        relation.setRuleVersionId(7L);
        relation.setTargetUserId(200L);
        relation.setTargetAgentName("上级会员");
        relation.setRelationLevel(1);
        relation.setRelationPath("100/200");
        relation.setSnapshotTime(paidTime.plusSeconds(1));
        when(relationSnapshotDao.selectByOrderId(10L)).thenReturn(List.of(relation));

        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setId(7L);
        version.setVersionNo("CUSTOMER_POLICY_V1");
        version.setVersionName("客户奖金程序 V1");
        when(ruleVersionDao.selectById(1L, 7L)).thenReturn(version);

        DmsShopMember receiver = new DmsShopMember();
        receiver.setUserId(200L);
        receiver.setUsername("receiver_200");
        receiver.setNickname("上级会员");
        when(memberDao.selectByUserId(200L)).thenReturn(receiver);

        DmsCommissionRecord record = new DmsCommissionRecord();
        record.setId(30L);
        record.setRuleVersionId(7L);
        record.setRecordNo("COM30");
        record.setAgentId(20L);
        record.setAgentUserId(200L);
        record.setAgentName("上级会员");
        record.setCommissionAmount(new BigDecimal("100.00"));
        record.setStatus(1);
        record.setCreateTime(paidTime.plusSeconds(2));
        record.setSettleTime(paidTime.plusDays(8));

        CommissionRecordVO actual = new CommissionRecordVO();
        actual.setId(30L);
        actual.setRecordNo("COM30");
        actual.setCommissionAmount(new BigDecimal("100.00"));
        actual.setStatus(1);

        DmsBonusCalculationSnapshot evidence = new DmsBonusCalculationSnapshot();
        evidence.setId(40L);
        evidence.setRuleVersionId(7L);
        evidence.setTotalPv(new BigDecimal("200.00"));
        evidence.setTotalBonus(new BigDecimal("100.00"));
        evidence.setRiskStatus("PASS");
        evidence.setCreateTime(paidTime.plusSeconds(3));
        when(calculationSnapshotDao.selectByOrderId(10L)).thenReturn(List.of(evidence));

        DmsMemberAssetFlow settlement = new DmsMemberAssetFlow();
        settlement.setId(50L);
        settlement.setFlowNo("FLOW50");
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setBalanceBefore(BigDecimal.ZERO);
        settlement.setBalanceAfter(new BigDecimal("100.00"));
        settlement.setCreateTime(record.getSettleTime());
        when(assetFlowDao.selectCommissionSettlementFlows(20L, 30L)).thenReturn(List.of(settlement));

        DmsFinanceRefund refund = new DmsFinanceRefund();
        refund.setId(60L);
        refund.setRefundNo("REF60");
        refund.setRefundAmount(new BigDecimal("20.00"));
        refund.setRefundTime(paidTime.plusDays(9));

        DmsCommissionClawback clawback = new DmsCommissionClawback();
        clawback.setId(70L);
        clawback.setRefundId(60L);
        clawback.setCommissionRecordId(30L);
        clawback.setAgentId(20L);
        clawback.setAgentUserId(200L);
        clawback.setAgentName("上级会员");
        clawback.setOriginalCommissionAmount(new BigDecimal("100.00"));
        clawback.setClawbackAmount(new BigDecimal("20.00"));
        clawback.setDeductedAmount(new BigDecimal("20.00"));
        clawback.setDebtAmount(BigDecimal.ZERO);
        clawback.setClawbackType(2);
        clawback.setStatus(1);
        clawback.setCreateTime(paidTime.plusDays(9));

        DmsMemberAssetFlow deduction = new DmsMemberAssetFlow();
        deduction.setId(51L);
        deduction.setFlowNo("FLOW51");
        deduction.setAmount(new BigDecimal("20.00"));
        deduction.setBalanceBefore(new BigDecimal("100.00"));
        deduction.setBalanceAfter(new BigDecimal("80.00"));
        deduction.setCreateTime(clawback.getCreateTime());
        when(assetFlowDao.selectCommissionClawbackFlows(20L, 60L, "COM30")).thenReturn(List.of(deduction));

        OrderBonusTraceVO trace = traceService.build(order, List.of(record), List.of(actual),
                List.of(refund), List.of(clawback));

        assertEquals("REFUND_ADJUSTED", trace.getStatus());
        assertEquals(new BigDecimal("100.00"), trace.getCalculatedAmount());
        assertEquals(new BigDecimal("100.00"), trace.getWalletIssuedAmount());
        assertEquals(new BigDecimal("20.00"), trace.getClawbackAmount());
        assertEquals(new BigDecimal("80.00"), trace.getCurrentNetAmount());
        assertEquals(1, trace.getActualRecords().size());
        assertEquals("COM30", trace.getActualRecords().get(0).getRecordNo());
        assertEquals(2, trace.getAssetFlows().size());
        assertEquals(1, trace.getRelationChain().size());
        assertTrue(trace.getTimeline().stream().anyMatch(item -> "ORDER_REFUND".equals(item.getCode())));
        assertTrue(trace.getTimeline().stream().anyMatch(item -> "COMMISSION_CLAWBACK".equals(item.getCode())));
    }

    @Test
    void paidOrderWithoutFrozenRelationExplainsWhyNoBonusRecordExists() {
        DmsShopOrder order = order(11L, LocalDateTime.of(2026, 8, 30, 12, 0), 1);
        when(relationSnapshotDao.selectByOrderId(11L)).thenReturn(List.of());
        when(calculationSnapshotDao.selectByOrderId(11L)).thenReturn(List.of());

        OrderBonusTraceVO trace = traceService.build(order, List.of(), List.of(), List.of(), List.of());

        assertEquals("NOT_ENTERED", trace.getStatus());
        assertEquals("未进入奖金程序", trace.getStatusName());
        assertTrue(trace.getExplanation().contains("商品不计奖"));
        assertEquals(0, trace.getActualRecords().size());
    }

    @Test
    void doesNotDeductHistoricalDebtTwiceAfterReducedCommissionIsSettled() {
        LocalDateTime paidTime = LocalDateTime.of(2026, 8, 30, 13, 0);
        DmsShopOrder order = order(12L, paidTime, 3);
        when(relationSnapshotDao.selectByOrderId(12L)).thenReturn(List.of());
        when(calculationSnapshotDao.selectByOrderId(12L)).thenReturn(List.of());

        DmsCommissionRecord record = new DmsCommissionRecord();
        record.setId(31L);
        record.setRecordNo("COM31");
        record.setAgentId(21L);
        record.setAgentUserId(201L);
        record.setAgentName("抵扣会员");
        record.setCommissionAmount(new BigDecimal("80.00"));
        record.setStatus(1);
        record.setCreateTime(paidTime.plusSeconds(1));
        record.setSettleTime(paidTime.plusDays(8));

        DmsCommissionClawback debtOffset = new DmsCommissionClawback();
        debtOffset.setId(71L);
        debtOffset.setRefundId(0L);
        debtOffset.setCommissionRecordId(31L);
        debtOffset.setAgentId(21L);
        debtOffset.setAgentUserId(201L);
        debtOffset.setAgentName("抵扣会员");
        debtOffset.setOriginalCommissionAmount(new BigDecimal("100.00"));
        debtOffset.setClawbackAmount(new BigDecimal("20.00"));
        debtOffset.setDeductedAmount(new BigDecimal("20.00"));
        debtOffset.setDebtAmount(BigDecimal.ZERO);
        debtOffset.setClawbackType(4);
        debtOffset.setStatus(1);
        debtOffset.setCreateTime(paidTime.plusSeconds(2));

        OrderBonusTraceVO trace = traceService.build(order, List.of(record), List.of(), List.of(), List.of(debtOffset));

        assertEquals("DEBT_OFFSET", trace.getStatus());
        assertEquals(new BigDecimal("100.00"), trace.getCalculatedAmount());
        assertEquals(new BigDecimal("80.00"), trace.getSettledNetAmount());
        assertEquals(new BigDecimal("80.00"), trace.getCurrentNetAmount());
        assertEquals(new BigDecimal("20.00"), trace.getClawbackAmount());
    }

    private DmsShopOrder order(Long id, LocalDateTime payTime, Integer status) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(id);
        order.setOrderNo("ORDER-" + id);
        order.setPayTime(payTime);
        order.setStatus(status);
        return order;
    }
}
