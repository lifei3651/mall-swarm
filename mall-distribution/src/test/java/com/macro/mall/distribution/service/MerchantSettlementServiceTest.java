package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.MerchantWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalPayDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalRejectDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalReviewDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantAccount;
import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.security.AdminContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.BeanUtils;
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
    @Autowired private ShopService shopService;
    @Autowired private OperationLogService operationLogService;
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

        // 商品目录成本随后由50改成80，已经下单的货款仍必须使用订单项中的50元快照。
        jdbcTemplate.update("UPDATE dms_shop_product SET cost_amount=80 WHERE id=1");
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
        Long withdrawalId = withdrawal.getId();

        MerchantWithdrawalPayDTO pay = new MerchantWithdrawalPayDTO();
        pay.setActualPaidAmount(new BigDecimal("500"));
        pay.setPaymentReference("BANK-TEST-001");
        merchantService.confirmPayment(withdrawalId, pay);
        DmsMerchantAccount paid = account(merchant.getId());
        assertMoney("0.00", paid.getAvailableAmount());
        assertMoney("0.00", paid.getFrozenAmount());
        assertMoney("500.00", paid.getTotalPaidAmount());
        assertThrows(RuntimeException.class, () -> merchantService.confirmPayment(withdrawalId, pay));
        MerchantWithdrawalRejectDTO paidReject = new MerchantWithdrawalRejectDTO();
        paidReject.setReason("已打款申请不能驳回");
        assertThrows(RuntimeException.class, () -> merchantService.rejectWithdrawal(withdrawalId, paidReject));
        assertMoney("500.00", account(merchant.getId()).getTotalPaidAmount());

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

    @Test
    void rejectionRestoresFrozenBalanceOnlyOnceAndOffsetsDebtFirst() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-REJECTION-TEST");
        merchant.setMerchantName("提现驳回测试商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("500"));
        DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);
        DmsMerchantAccount frozen = account(merchant.getId());
        assertMoney("0.00", frozen.getAvailableAmount());
        assertMoney("500.00", frozen.getFrozenAmount());

        MerchantWithdrawalRejectDTO blankReject = new MerchantWithdrawalRejectDTO();
        blankReject.setReason("   ");
        assertThrows(RuntimeException.class,
                () -> merchantService.rejectWithdrawal(withdrawal.getId(), blankReject));
        assertMoney("500.00", account(merchant.getId()).getFrozenAmount());

        MerchantWithdrawalReviewDTO pendingInvoice = new MerchantWithdrawalReviewDTO();
        pendingInvoice.setInvoiceRequiredAmount(new BigDecimal("500"));
        pendingInvoice.setInvoiceReceivedAmount(BigDecimal.ZERO);
        pendingInvoice.setInvoiceStatus("PENDING");
        pendingInvoice.setAdjustmentAmount(BigDecimal.ZERO);
        assertEquals("INVOICE_PENDING",
                merchantService.reviewWithdrawal(withdrawal.getId(), pendingInvoice).getStatus());
        MerchantWithdrawalPayDTO prematurePay = new MerchantWithdrawalPayDTO();
        prematurePay.setActualPaidAmount(new BigDecimal("500"));
        assertThrows(RuntimeException.class,
                () -> merchantService.confirmPayment(withdrawal.getId(), prematurePay));

        // 提现冻结后出现100元退款欠款，驳回时先抵欠款，剩余400元回到可提现。
        jdbcTemplate.update("UPDATE dms_merchant_account SET debt_amount=100 WHERE merchant_id=?", merchant.getId());
        MerchantWithdrawalRejectDTO reject = new MerchantWithdrawalRejectDTO();
        reject.setReason("发票信息不符合约定，退回重新申请");
        DmsMerchantWithdrawal rejected = merchantService.rejectWithdrawal(withdrawal.getId(), reject);
        assertEquals("REJECTED", rejected.getStatus());
        DmsMerchantAccount restored = account(merchant.getId());
        assertMoney("400.00", restored.getAvailableAmount());
        assertMoney("0.00", restored.getFrozenAmount());
        assertMoney("0.00", restored.getDebtAmount());

        assertThrows(RuntimeException.class, () -> merchantService.rejectWithdrawal(withdrawal.getId(), reject));
        DmsMerchantAccount unchanged = account(merchant.getId());
        assertMoney("400.00", unchanged.getAvailableAmount());
        assertMoney("0.00", unchanged.getFrozenAmount());
    }

    @Test
    void merchantSettlementCostRequiresFinancePermissionReasonAndAuditTrail() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-COST-GOVERNANCE");
        merchant.setMerchantName("成本权限测试商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=?,cost_amount=50,team_bonus_mode='NONE',status=0,merchant_review_status='APPROVED' WHERE id=1",
                merchant.getId(), merchant.getMerchantName());

        DmsShopProduct product = new DmsShopProduct();
        BeanUtils.copyProperties(shopService.getProduct(1L), product);
        product.setCostAmount(new BigDecimal("80"));
        product.setSettlementCostChangeReason("依据新供货合同调整结算成本");
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");
        product.setDeliveryAddress("湖南省 长沙市 岳麓区");

        DmsAdminUser productOperator = new DmsAdminUser();
        productOperator.setId(91001L);
        productOperator.setUsername("product-only");
        productOperator.setPermissions("shop:product");
        AdminContext.set(productOperator);
        try {
            assertThrows(RuntimeException.class, () -> shopService.updateProduct(1L, product));
            assertMoney("50.00", shopService.getProduct(1L).getCostAmount());

            DmsAdminUser financeOperator = new DmsAdminUser();
            financeOperator.setId(91002L);
            financeOperator.setUsername("finance-manager");
            financeOperator.setPermissions("shop:product,finance:manage");
            AdminContext.set(financeOperator);
            product.setSettlementCostChangeReason(null);
            assertThrows(RuntimeException.class, () -> shopService.updateProduct(1L, product));
            assertMoney("50.00", shopService.getProduct(1L).getCostAmount());
            product.setSettlementCostChangeReason("依据新供货合同调整结算成本");
            DmsShopProduct updated = shopService.updateProduct(1L, product);
            assertMoney("80.00", updated.getCostAmount());
            assertTrue(operationLogService.listLogs("MERCHANT_SETTLEMENT", "SHOP_PRODUCT", "1").stream()
                    .anyMatch(log -> "COST_CHANGE".equals(log.getOperationType())
                            && log.getBeforeData().contains("productCost=50.00")
                            && log.getAfterData().contains("productCost=80.00")
                            && log.getRemark().contains("新供货合同")));
        } finally {
            AdminContext.clear();
        }
    }

    @Test
    void merchantSkuCostCannotExceedInheritedRepurchasePrice() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-SKU-COST-LIMIT");
        merchant.setMerchantName("SKU复购成本测试商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("""
                UPDATE dms_shop_product
                SET merchant_id=?,merchant_name=?,cost_amount=50,team_bonus_mode='NONE',
                    normal_sale_enabled=0,repurchase_sale_enabled=1,repurchase_price=80
                WHERE id=1
                """, merchant.getId(), merchant.getMerchantName());

        ShopSkuDTO sku = new ShopSkuDTO();
        sku.setProductId(1L);
        sku.setSkuName("复购规格");
        sku.setAttrsJson("{\"规格\":\"复购规格\"}");
        sku.setSalePrice(new BigDecimal("200"));
        sku.setCostAmount(new BigDecimal("100"));
        sku.setStock(10);
        sku.setStatus(1);

        assertThrows(RuntimeException.class, () -> shopService.saveSku(sku));
    }

    private DmsMerchantAccount account(Long merchantId) {
        return merchantService.listAccounts(null).stream()
                .filter(item -> merchantId.equals(item.getMerchantId())).findFirst().orElseThrow();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
