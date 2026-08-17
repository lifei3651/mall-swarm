package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.MerchantWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalPayDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalReviewDTO;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantAccount;
import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantSettlementServiceTest {
    @Autowired private MerchantService merchantService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void costSnapshotSettlementInvoicePaymentAndRefundDebtFormAClosedLedger() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-SETTLEMENT-TEST");
        merchant.setMerchantName("成本结算测试商户");
        merchant = merchantService.saveMerchant(merchant);

        long orderId = 9966001L;
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,merchant_id,merchant_name,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,status,pay_type,pay_time,receive_time)
                VALUES (?,?,?,?,?,1001,'测试会员','13800000000','测试地址',990,0,0,990,0,500,'NORMAL',3,'BALANCE',?,?)
                """, orderId, "MO9966001", 1L, merchant.getId(), merchant.getMerchantName(),
                LocalDateTime.now().minusDays(8), LocalDateTime.now().minusDays(8));
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (order_id,order_no,merchant_id,merchant_name,product_id,product_name,price,quantity,total_amount,
                 pv_value,total_pv,cost_amount,total_cost,team_bonus_mode)
                VALUES (?,?,?,?,1,'商户测试商品',99,10,990,0,0,50,500,'NONE')
                """, orderId, "MO9966001", merchant.getId(), merchant.getMerchantName());
        Long orderItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_shop_order_item WHERE order_id=?", Long.class, orderId);

        merchantService.createOrderSettlements(orderId);
        DmsMerchantAccount pending = account(merchant.getId());
        assertMoney("500.00", pending.getPendingAmount());
        assertMoney("0.00", pending.getAvailableAmount());

        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_days=0 WHERE id=1");
        assertEquals(1, merchantService.releaseEligibleSettlements(20));
        DmsMerchantAccount available = account(merchant.getId());
        assertMoney("0.00", available.getPendingAmount());
        assertMoney("500.00", available.getAvailableAmount());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("500"));
        DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);

        MerchantWithdrawalReviewDTO review = new MerchantWithdrawalReviewDTO();
        review.setInvoiceRequiredAmount(new BigDecimal("500"));
        review.setInvoiceReceivedAmount(new BigDecimal("500"));
        review.setInvoiceStatus("RECEIVED");
        review.setAdjustmentAmount(BigDecimal.ZERO);
        withdrawal = merchantService.reviewWithdrawal(withdrawal.getId(), review);
        assertEquals("READY_TO_PAY", withdrawal.getStatus());

        MerchantWithdrawalPayDTO pay = new MerchantWithdrawalPayDTO();
        pay.setActualPaidAmount(new BigDecimal("500"));
        pay.setPaymentReference("BANK-TEST-001");
        merchantService.confirmPayment(withdrawal.getId(), pay);
        DmsMerchantAccount paid = account(merchant.getId());
        assertMoney("0.00", paid.getAvailableAmount());
        assertMoney("0.00", paid.getFrozenAmount());
        assertMoney("500.00", paid.getTotalPaidAmount());

        DmsShopAfterSaleItem refundItem = new DmsShopAfterSaleItem();
        refundItem.setOrderItemId(orderItemId);
        refundItem.setRefundQuantity(2);
        merchantService.reverseAfterSaleItems(List.of(refundItem));
        DmsMerchantAccount refunded = account(merchant.getId());
        assertMoney("100.00", refunded.getDebtAmount());
        assertMoney("0.00", refunded.getAvailableAmount());

        assertThrows(NoSuchFieldException.class, () -> DmsMerchantWithdrawal.class.getDeclaredField("taxAmount"));
    }

    @Test
    void payoutAdjustmentIsNotReturnedAndMustCoverDebtCreatedAfterFreeze() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-ADJUSTMENT-TEST");
        merchant.setMerchantName("提现调整测试商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("500"));
        DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);
        jdbcTemplate.update("UPDATE dms_merchant_account SET debt_amount=100 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalReviewDTO noAdjustment = new MerchantWithdrawalReviewDTO();
        noAdjustment.setInvoiceStatus("NOT_REQUIRED");
        noAdjustment.setAdjustmentAmount(BigDecimal.ZERO);
        merchantService.reviewWithdrawal(withdrawal.getId(), noAdjustment);
        MerchantWithdrawalPayDTO fullPay = new MerchantWithdrawalPayDTO();
        fullPay.setActualPaidAmount(new BigDecimal("500"));
        assertThrows(RuntimeException.class, () -> merchantService.confirmPayment(withdrawal.getId(), fullPay));

        MerchantWithdrawalReviewDTO adjusted = new MerchantWithdrawalReviewDTO();
        adjusted.setInvoiceStatus("NOT_REQUIRED");
        adjusted.setAdjustmentAmount(new BigDecimal("-100"));
        adjusted.setAdjustmentReason("冻结后发生退款，抵扣商户欠款");
        merchantService.reviewWithdrawal(withdrawal.getId(), adjusted);
        MerchantWithdrawalPayDTO reducedPay = new MerchantWithdrawalPayDTO();
        reducedPay.setActualPaidAmount(new BigDecimal("400"));
        merchantService.confirmPayment(withdrawal.getId(), reducedPay);

        DmsMerchantAccount paid = account(merchant.getId());
        assertMoney("0.00", paid.getAvailableAmount());
        assertMoney("0.00", paid.getFrozenAmount());
        assertMoney("0.00", paid.getDebtAmount());
        assertMoney("400.00", paid.getTotalPaidAmount());
    }

    private DmsMerchantAccount account(Long merchantId) {
        return merchantService.listAccounts(null).stream()
                .filter(item -> merchantId.equals(item.getMerchantId())).findFirst().orElseThrow();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
