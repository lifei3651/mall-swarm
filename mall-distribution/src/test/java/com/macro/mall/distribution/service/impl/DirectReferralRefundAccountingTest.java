package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.bonus.CustomerBonusPolicy;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyRegistry;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the public refund gate and its commission, account and finance effects. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectReferralRefundAccountingTest {
    @Mock DmsOrderFinanceDao financeDao;
    @Mock DmsShopAfterSaleItemDao afterSaleItemDao;
    @Mock DmsShopAfterSaleDao afterSaleDao;
    @Mock DmsShopOrderItemDao orderItemDao;
    @Mock DmsOrderCompanyShareDao companyShareDao;
    @Mock DmsFinanceRefundDao refundDao;
    @Mock DmsFinanceRiskRuleDao riskRuleDao;
    @Mock DmsCommissionClawbackDao clawbackDao;
    @Mock DmsCommissionRecordDao commissionRecordDao;
    @Mock DmsCommissionRuleVersionDao ruleVersionDao;
    @Mock DmsOrderRelationSnapshotDao relationSnapshotDao;
    @Mock DmsAgentAccountDao accountDao;
    @Mock DmsShopOrderDao shopOrderDao;
    @Mock DmsMemberAssetFlowDao memberAssetFlowDao;
    @Mock DmsMemberAssetAccountDao memberAssetAccountDao;
    @Mock PerformanceService performanceService;
    @Mock MemberAssetService memberAssetService;
    @Mock CustomerBonusPolicyRegistry bonusPolicyRegistry;
    @Mock CustomerBonusPolicy frozenPolicy;
    @InjectMocks DistributionAuditServiceImpl service;

    private final List<DmsFinanceRefund> refunds = new ArrayList<>();
    private final List<DmsCommissionClawback> clawbacks = new ArrayList<>();
    private final List<DmsShopAfterSale> sales = new ArrayList<>();
    private final Map<Long, List<DmsShopAfterSaleItem>> saleLines = new LinkedHashMap<>();
    private final Map<Long, DmsCommissionClawback> historicalDebts = new LinkedHashMap<>();
    private final List<DmsShopOrderItem> orderItems = new ArrayList<>();
    private DmsShopOrder order;
    private DmsOrderFinance finance;
    private DmsCommissionRecord commission;
    private DmsAgentAccount account;

    @BeforeEach
    void setup() {
        order = new DmsShopOrder();
        order.setId(100L);
        order.setOrderNo("ORDER-B-100");
        order.setUserId(202L); // B is an ordinary customer; only A owns the commission account.
        order.setStatus(1);
        order.setPayTime(LocalDateTime.of(2026, 9, 30, 9, 0));
        order.setCouponClaimId(7L);
        order.setTotalAmount(money("200.00"));
        order.setDiscountAmount(money("100.00"));
        order.setFreightAmount(money("5.00"));
        order.setPayAmount(money("105.00"));
        orderItems.add(orderItem(11L, "STANDARD", "100.00", "50.00", 2));
        orderItems.add(orderItem(12L, "NONE", "100.00", "50.00", 2));
        finance = new DmsOrderFinance();
        finance.setOrderId(order.getId());
        finance.setOrderNo(order.getOrderNo());
        finance.setPayAmount(order.getPayAmount());
        finance.setProductCost(BigDecimal.ZERO);
        commission = new DmsCommissionRecord();
        commission.setId(500L);
        commission.setOrderId(order.getId());
        commission.setOrderNo(order.getOrderNo());
        commission.setOrderUserId(order.getUserId());
        commission.setAgentId(101L);
        commission.setAgentUserId(201L);
        commission.setBonusType("DIRECT_REFERRAL");
        commission.setRuleVersionId(20L);
        commission.setCommissionRate(money("0.10"));
        commission.setOrderAmount(money("50.00"));
        commission.setCommissionAmount(money("5.00"));
        commission.setStatus(0);
        account = new DmsAgentAccount();
        account.setAgentId(commission.getAgentId());
        account.setTotalCommission(money("5.00"));
        account.setUnsettledCommission(money("5.00"));
        account.setSettledCommission(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);

        when(financeDao.selectByOrderId(order.getId())).thenReturn(finance);
        when(shopOrderDao.selectById(order.getId())).thenReturn(order);
        when(shopOrderDao.selectByIdForUpdate(order.getId())).thenReturn(order);
        when(orderItemDao.selectByOrderId(order.getId())).thenAnswer(invocation -> orderItems);
        when(afterSaleDao.selectByOrderId(order.getId())).thenAnswer(invocation -> sales);
        when(afterSaleItemDao.selectByAfterSaleId(anyLong()))
                .thenAnswer(invocation -> saleLines.get(invocation.getArgument(0)));
        when(refundDao.selectByOrderId(order.getId())).thenAnswer(invocation -> new ArrayList<>(refunds));
        when(refundDao.insert(any())).thenAnswer(invocation -> {
            DmsFinanceRefund refund = invocation.getArgument(0);
            refund.setId(900L + refunds.size());
            refunds.add(refund);
            return 1;
        });
        when(refundDao.sumProductByOrderId(order.getId())).thenAnswer(invocation ->
                refunds.stream().map(DmsFinanceRefund::getProductRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(refundDao.sumFreightByOrderId(order.getId())).thenAnswer(invocation ->
                refunds.stream().map(DmsFinanceRefund::getFreightRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(refundDao.sumByOrderId(order.getId())).thenAnswer(invocation ->
                refunds.stream().map(DmsFinanceRefund::getRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(commissionRecordDao.selectByOrderId(order.getId())).thenAnswer(invocation -> List.of(commission));
        when(commissionRecordDao.selectByIdForUpdate(commission.getId())).thenReturn(commission);
        when(commissionRecordDao.updateAmountAndStatus(anyLong(), any(), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    commission.setCommissionAmount(invocation.getArgument(1));
                    commission.setStatus(invocation.getArgument(2));
                    return 1;
                });
        when(clawbackDao.sumByCommissionRecordId(commission.getId())).thenAnswer(invocation ->
                clawbacks.stream().map(DmsCommissionClawback::getClawbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(clawbackDao.selectByOrderId(order.getId())).thenAnswer(invocation -> new ArrayList<>(clawbacks));
        when(clawbackDao.selectByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> historicalDebts.get(invocation.getArgument(0)));
        when(clawbackDao.updateDebtAfterOffset(anyLong(), any(), any(), anyInt())).thenAnswer(invocation -> {
            DmsCommissionClawback row = historicalDebts.get(invocation.getArgument(0));
            row.setDeductedAmount(invocation.getArgument(1));
            row.setDebtAmount(invocation.getArgument(2));
            row.setStatus(invocation.getArgument(3));
            return 1;
        });
        when(clawbackDao.insert(any())).thenAnswer(invocation -> {
            DmsCommissionClawback row = invocation.getArgument(0);
            row.setId(1000L + clawbacks.size());
            clawbacks.add(row);
            return 1;
        });
        when(accountDao.subtractTotalCommission(anyLong(), any())).thenAnswer(invocation -> {
            account.setTotalCommission(account.getTotalCommission().subtract(invocation.getArgument(1)));
            return 1;
        });
        when(accountDao.subtractUnsettledCommission(anyLong(), any())).thenAnswer(invocation -> {
            account.setUnsettledCommission(account.getUnsettledCommission().subtract(invocation.getArgument(1)));
            return 1;
        });
        when(accountDao.subtractSettledCommission(anyLong(), any())).thenAnswer(invocation -> {
            account.setSettledCommission(account.getSettledCommission().subtract(invocation.getArgument(1)));
            return 1;
        });
        when(accountDao.subtractAvailableBalance(anyLong(), any())).thenAnswer(invocation -> {
            account.setAvailableBalance(account.getAvailableBalance().subtract(invocation.getArgument(1)));
            return 1;
        });
        when(accountDao.selectByAgentIdForUpdate(commission.getAgentId())).thenReturn(account);
        DmsOrderRelationSnapshot frozen = new DmsOrderRelationSnapshot();
        frozen.setRuleVersionId(20L);
        when(relationSnapshotDao.selectByOrderId(order.getId())).thenReturn(List.of(frozen));
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setId(20L);
        version.setTenantId(1L);
        version.setVersionNo("DIRECT_REFERRAL_V1");
        version.setStatus(0); // Archived by a later configuration; the order keeps this version.
        when(ruleVersionDao.selectById(1L, 20L)).thenReturn(version);
        when(bonusPolicyRegistry.require("DIRECT_REFERRAL_V1")).thenReturn(frozenPolicy);
    }

    @Test
    void refundOfNonparticipatingGoodsDoesNotTakeBackInviterCommission() {
        service.saveRefund(request("AS-NONE", 12L, 1, "25.00", "0.00"));

        assertMoney("5.00", commission.getCommissionAmount());
        assertMoney("5.00", account.getTotalCommission());
        assertMoney("5.00", finance.getBonusAmount());
        assertMoney("80.00", finance.getNetPayAmount());
        assertTrue(clawbacks.isEmpty());
    }

    @Test
    void couponRefundReversesOnlyBuyerCashAndFinalQuantityClearsCommission() {
        // Platform paid the other 50.00 discount; legacy bonus delta is 50.00,
        // while buyer cash refund is only 25.00. Direct rewards use the latter.
        service.saveRefund(request("AS-HALF", 11L, 1, "25.00", "50.00"));
        assertMoney("2.50", commission.getCommissionAmount());
        assertMoney("2.50", account.getUnsettledCommission());
        assertMoney("2.50", finance.getBonusAmount());
        assertMoney("2.50", clawbacks.get(0).getClawbackAmount());

        service.saveRefund(request("AS-REST", 11L, 1, "25.00", "50.00"));
        assertMoney("0.00", commission.getCommissionAmount());
        assertEquals(3, commission.getStatus());
        assertMoney("0.00", account.getUnsettledCommission());
        assertMoney("0.00", account.getTotalCommission());
        assertMoney("0.00", finance.getBonusAmount());
        assertMoney("55.00", finance.getNetPayAmount());
        assertMoney("5.00", clawbacks.stream().map(DmsCommissionClawback::getClawbackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(ruleVersionDao, never()).selectActiveByTenantId(anyLong());
    }

    @Test
    void unconfirmedRefundRequestsDoNotContributeToCumulativeReversal() {
        request("AS-PROCESSING", 11L, 1, "25.00", "50.00");
        sales.get(0).setStatus(6); // No successful financial refund ledger.
        service.saveRefund(request("AS-NONE-COMPLETED", 12L, 1, "25.00", "0.00"));

        assertMoney("5.00", commission.getCommissionAmount());
        assertTrue(clawbacks.isEmpty());
    }

    @Test
    void repeatedSuccessfulRefundNumberReturnsRecordedResultWithoutAnotherMutation() {
        FinanceRefundDTO dto = request("AS-DUPLICATE", 11L, 1, "25.00", "50.00");
        DmsFinanceRefund first = service.saveRefund(dto);
        assertSame(first, service.saveRefund(dto));

        assertEquals(1, refunds.size());
        assertEquals(1, clawbacks.size());
        assertMoney("2.50", account.getUnsettledCommission());
        verify(refundDao, times(1)).insert(any());
        verify(performanceService, times(1)).reverseOrderPerformance(anyLong(), anyLong(), any(), anyInt(), any());
        verify(frozenPolicy, times(1)).afterRefund(any());
    }

    @Test
    void settledRewardUsesExistingWalletThenCashThenDebtReversal() {
        commission.setStatus(1);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setSettledCommission(money("5.00"));
        account.setAvailableBalance(money("1.00"));
        DmsMemberAssetAccount wallet = new DmsMemberAssetAccount();
        wallet.setBalance(money("2.00"));
        when(memberAssetFlowDao.selectCommissionSettlementFlows(101L, 500L))
                .thenReturn(List.of(new DmsMemberAssetFlow()));
        when(memberAssetAccountDao.selectByAgentIdAndAssetCode(eq(101L), anyString())).thenReturn(wallet);

        service.saveRefund(request("AS-SETTLED-FULL", 11L, 2, "50.00", "100.00"));

        ArgumentCaptor<AssetChangeDTO> deduction = ArgumentCaptor.forClass(AssetChangeDTO.class);
        verify(memberAssetService).deduct(deduction.capture());
        assertMoney("2.00", deduction.getValue().getAmount());
        assertEquals("COMMISSION_CLAWBACK-900-500", deduction.getValue().getRequestId());
        assertMoney("0.00", account.getTotalCommission());
        assertMoney("0.00", account.getSettledCommission());
        assertMoney("0.00", account.getAvailableBalance());
        assertMoney("5.00", clawbacks.get(0).getClawbackAmount());
        assertMoney("3.00", clawbacks.get(0).getDeductedAmount());
        assertMoney("2.00", clawbacks.get(0).getDebtAmount());
        assertEquals(3, clawbacks.get(0).getClawbackType());
        assertMoney("0.00", finance.getBonusAmount());
    }

    @Test
    void repeatedPartialQuantityRefundsRemoveFinalRoundingCent() {
        order.setCouponClaimId(null);
        order.setTotalAmount(money("0.30"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(money("0.30"));
        finance.setPayAmount(order.getPayAmount());
        orderItems.clear();
        orderItems.add(orderItem(11L, "STANDARD", "0.10", "0.00", 3));
        orderItems.add(orderItem(12L, "NONE", "0.20", "0.00", 1));
        commission.setOrderAmount(money("0.10"));
        commission.setCommissionRate(money("0.33"));
        commission.setCommissionAmount(money("0.03"));
        account.setTotalCommission(money("0.03"));
        account.setUnsettledCommission(money("0.03"));

        service.saveRefund(request("AS-CENT-1", 11L, 1, "0.03", "0.03"));
        assertMoney("0.02", commission.getCommissionAmount());
        service.saveRefund(request("AS-CENT-2", 11L, 1, "0.03", "0.03"));
        assertMoney("0.01", commission.getCommissionAmount());
        service.saveRefund(request("AS-CENT-3", 11L, 1, "0.04", "0.04"));
        assertMoney("0.00", commission.getCommissionAmount());
        assertMoney("0.00", account.getUnsettledCommission());
        assertMoney("0.00", finance.getBonusAmount());
    }

    @Test
    void missingRefundItemSnapshotStopsDirectClawbackInsteadOfWholeOrderRatio() {
        FinanceRefundDTO dto = request("AS-MISSING", 11L, 1, "25.00", "50.00");
        saleLines.clear();
        assertThrows(ApiException.class, () -> service.saveRefund(dto));
        verifyNoInteractions(memberAssetService);
        verify(accountDao, never()).subtractUnsettledCommission(anyLong(), any());
    }

    @Test
    void refundBeforeSettlementThenRemainingRefundUsesOriginalFrozenAward() {
        service.saveRefund(request("AS-PENDING-HALF", 11L, 1, "25.00", "50.00"));
        commission.setStatus(1);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setSettledCommission(money("2.50"));
        account.setAvailableBalance(money("2.50"));

        service.saveRefund(request("AS-SETTLED-REST", 11L, 1, "25.00", "50.00"));

        assertMoney("0.00", account.getTotalCommission());
        assertMoney("0.00", account.getSettledCommission());
        assertMoney("0.00", account.getAvailableBalance());
        assertMoney("0.00", finance.getBonusAmount());
        assertMoney("5.00", clawbacks.stream().filter(row -> row.getClawbackType() < 4)
                .map(DmsCommissionClawback::getClawbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void fullyDebtOffsetSettledAwardRestoresOriginalDebtAcrossPartialRefunds() {
        markSettled();
        DmsCommissionClawback debt = offsetDebt(2L, "5.00");
        service.saveRefund(request("AS-OFFSET-HALF", 11L, 1, "25.00", "50.00"));

        assertMoney("2.50", debt.getDebtAmount());
        assertMoney("2.50", debt.getDeductedAmount());
        assertMoney("2.50", account.getSettledCommission());
        assertMoney("2.50", finance.getBonusAmount());
        service.saveRefund(request("AS-OFFSET-REST", 11L, 1, "25.00", "50.00"));

        assertMoney("5.00", debt.getDebtAmount());
        assertMoney("0.00", debt.getDeductedAmount());
        assertMoney("0.00", account.getSettledCommission());
        assertMoney("0.00", account.getTotalCommission());
        assertMoney("0.00", finance.getBonusAmount());
        assertMoney("5.00", clawbacks.stream().filter(row -> row.getClawbackType() == 5)
                .map(DmsCommissionClawback::getClawbackAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertMoney("0.00", clawbacks.stream().map(DmsCommissionClawback::getDebtAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)); // Debt exists once, on the original row only.
        verifyNoInteractions(memberAssetService);
    }

    @Test
    void mixedDebtAndWalletAwardReversesEachPortionWithoutDoubleDeduction() {
        markSettled();
        DmsCommissionClawback debt = offsetDebt(2L, "2.00");
        DmsMemberAssetAccount wallet = new DmsMemberAssetAccount();
        wallet.setBalance(money("3.00"));
        when(memberAssetFlowDao.selectCommissionSettlementFlows(101L, 500L))
                .thenReturn(List.of(new DmsMemberAssetFlow()));
        when(memberAssetAccountDao.selectByAgentIdAndAssetCode(eq(101L), anyString())).thenReturn(wallet);
        when(memberAssetService.deduct(any())).thenAnswer(invocation -> {
            AssetChangeDTO dto = invocation.getArgument(0);
            wallet.setBalance(wallet.getBalance().subtract(dto.getAmount()));
            return new DmsMemberAssetFlow();
        });

        service.saveRefund(request("AS-MIXED-HALF", 11L, 1, "25.00", "50.00"));
        assertMoney("1.00", debt.getDebtAmount());
        assertMoney("1.50", wallet.getBalance());
        assertMoney("2.50", finance.getBonusAmount());
        service.saveRefund(request("AS-MIXED-REST", 11L, 1, "25.00", "50.00"));
        assertMoney("2.00", debt.getDebtAmount());
        assertMoney("0.00", wallet.getBalance());
        assertMoney("0.00", finance.getBonusAmount());
        assertMoney("0.00", account.getTotalCommission());
    }

    @Test
    void multipleOneCentDebtOffsetsNeverRestoreMoreThanOneCentRefund() {
        order.setCouponClaimId(null);
        order.setTotalAmount(money("0.20"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(money("0.20"));
        finance.setPayAmount(order.getPayAmount());
        orderItems.clear();
        orderItems.add(orderItem(11L, "STANDARD", "0.20", "0.00", 2));
        commission.setOrderAmount(money("0.20"));
        commission.setCommissionAmount(money("0.02"));
        account.setTotalCommission(money("0.02"));
        markSettled();
        DmsCommissionClawback firstDebt = offsetDebt(2L, "0.01");
        DmsCommissionClawback secondDebt = offsetDebt(3L, "0.01");

        service.saveRefund(request("AS-TINY-HALF", 11L, 1, "0.10", "0.10"));
        assertMoney("0.01", firstDebt.getDebtAmount().add(secondDebt.getDebtAmount()));
        assertMoney("0.01", finance.getBonusAmount());
        service.saveRefund(request("AS-TINY-REST", 11L, 1, "0.10", "0.10"));
        assertMoney("0.02", firstDebt.getDebtAmount().add(secondDebt.getDebtAmount()));
        assertMoney("0.00", finance.getBonusAmount());
        verifyNoInteractions(memberAssetService);
    }

    @Test
    void legacyBonusKeepsExistingCumulativeBonusBaseBehavior() {
        commission.setBonusType("CUSTOMER_LEGACY_BONUS");
        service.saveRefund(request("AS-LEGACY", 11L, 1, "25.00", "50.00"));

        assertMoney("2.50", commission.getCommissionAmount());
        verify(orderItemDao, never()).selectByOrderId(anyLong());
        verify(commissionRecordDao, never()).selectByIdForUpdate(anyLong());
    }

    private FinanceRefundDTO request(String number, Long itemId, int quantity, String cash, String legacyBonus) {
        long saleId = 700L + sales.size();
        DmsShopAfterSale sale = new DmsShopAfterSale();
        sale.setId(saleId);
        sale.setOrderId(order.getId());
        sale.setAfterSaleNo(number);
        sale.setStatus(1);
        sale.setApplyType(2);
        sales.add(sale);
        DmsShopOrderItem original = orderItems.stream().filter(item -> itemId.equals(item.getId())).findFirst().orElseThrow();
        DmsShopAfterSaleItem line = new DmsShopAfterSaleItem();
        line.setAfterSaleId(saleId);
        line.setOrderId(order.getId());
        line.setOrderItemId(itemId);
        line.setProductId(original.getProductId());
        line.setSkuId(original.getSkuId());
        line.setRefundQuantity(quantity);
        line.setRefundAmount(money(cash));
        line.setCouponBonusRefundAmount(money(legacyBonus));
        saleLines.put(saleId, List.of(line));
        FinanceRefundDTO dto = new FinanceRefundDTO();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setRefundNo(number);
        dto.setProductRefundAmount(money(cash));
        dto.setFreightRefundAmount(BigDecimal.ZERO);
        dto.setRefundQuantity(quantity);
        dto.setBonusBaseAmount(money("100.00"));
        dto.setBonusRefundAmount(money(legacyBonus));
        dto.setBonusRefundQuantity("NONE".equals(original.getTeamBonusMode()) ? 0 : quantity);
        dto.setCumulativeBonusRefundAmount(saleLines.values().stream().flatMap(List::stream)
                .map(DmsShopAfterSaleItem::getCouponBonusRefundAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        dto.setReason("测试退款");
        return dto;
    }

    private DmsShopOrderItem orderItem(Long id, String mode, String gross, String discount, int quantity) {
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setId(id);
        item.setOrderId(order.getId());
        item.setProductId(id + 1000L);
        item.setSkuId(id + 2000L);
        item.setTeamBonusMode(mode);
        item.setTotalAmount(money(gross));
        item.setCouponDiscountAmount(money(discount));
        item.setQuantity(quantity);
        return item;
    }

    private void markSettled() {
        commission.setStatus(1);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setSettledCommission(commission.getCommissionAmount());
    }

    private DmsCommissionClawback offsetDebt(Long sourceId, String amount) {
        DmsCommissionClawback source = new DmsCommissionClawback();
        source.setId(sourceId);
        source.setAgentId(commission.getAgentId());
        source.setClawbackType(3);
        source.setClawbackAmount(money(amount));
        source.setDeductedAmount(money(amount));
        source.setDebtAmount(BigDecimal.ZERO);
        source.setStatus(1);
        historicalDebts.put(sourceId, source);
        DmsCommissionClawback offset = new DmsCommissionClawback();
        offset.setId(600L + clawbacks.size());
        offset.setOrderId(order.getId());
        offset.setCommissionRecordId(commission.getId());
        offset.setSourceClawbackId(sourceId);
        offset.setClawbackType(4);
        offset.setClawbackAmount(money(amount));
        offset.setDeductedAmount(money(amount));
        offset.setDebtAmount(BigDecimal.ZERO);
        clawbacks.add(offset);
        return source;
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual), "expected " + expected + ", actual " + actual);
    }
}
