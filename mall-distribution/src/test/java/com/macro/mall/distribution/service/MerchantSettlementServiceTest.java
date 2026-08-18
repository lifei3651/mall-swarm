package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.MerchantWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.MerchantDepositAdjustDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalPayDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalRejectDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalReviewDTO;
import com.macro.mall.distribution.dto.MerchantControlDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalActionDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantAccount;
import com.macro.mall.distribution.entity.DmsMerchantDepositFlow;
import com.macro.mall.distribution.entity.DmsMerchantSettlement;
import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.MerchantBalanceReconciliationVO;
import com.macro.mall.distribution.vo.MerchantExitReadinessVO;
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
    @Autowired private ShopAfterSaleService shopAfterSaleService;
    @Autowired private OperationLogService operationLogService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void costSnapshotSettlementInvoicePaymentAndRefundDebtFormAClosedLedger() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-SETTLEMENT-TEST");
        merchant.setMerchantName("成本结算测试商户");
        preparePayoutProfile(merchant);
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
        apply.setRequestNo("WITHDRAW-CLOSED-LEDGER");
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

        assertEquals(6, merchantService.listLedgers(merchant.getId(), null).size());
        assertEquals(4, merchantService.listWithdrawalEvents(withdrawalId).size());
        assertTrue(merchantService.listLedgers(merchant.getId(), "AFTER_SALE_REVERSAL").stream()
                .anyMatch(row -> row.getDebtDelta().compareTo(new BigDecimal("100.00")) == 0));

        assertThrows(NoSuchFieldException.class, () -> DmsMerchantWithdrawal.class.getDeclaredField("taxAmount"));
    }

    @Test
    void payoutAdjustmentIsNotReturnedAndMustCoverDebtCreatedAfterFreeze() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-ADJUSTMENT-TEST");
        merchant.setMerchantName("提现调整测试商户");
        preparePayoutProfile(merchant);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setRequestNo("WITHDRAW-ADJUSTMENT");
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
        preparePayoutProfile(merchant);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setRequestNo("WITHDRAW-REJECTION");
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

    @Test
    void settlementDelayIsSnapshottedPerOrderItemAndStartsAfterReceiptAndAfterSaleWindow() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-DELAY-SNAPSHOT");
        merchant.setMerchantName("结算周期测试商户");
        merchant.setDefaultSettlementDays(7);
        merchant = merchantService.saveMerchant(merchant);
        assertEquals(7, merchant.getDefaultSettlementDays());

        long orderId = 9966010L;
        LocalDateTime receivedAt = LocalDateTime.now().minusDays(15);
        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_days=0 WHERE id=1");
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,merchant_id,merchant_name,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,status,pay_type,pay_time,receive_time)
                VALUES (?,?,?,?,?,1001,'测试会员','13800000000','测试地址',99,0,0,99,0,50,'NORMAL',3,'BALANCE',?,?)
                """, orderId, "MO9966010", 1L, merchant.getId(), merchant.getMerchantName(),
                LocalDateTime.now().minusDays(20), receivedAt);
        jdbcTemplate.update("UPDATE dms_shop_order SET create_time=? WHERE id=?", LocalDateTime.now().minusDays(40), orderId);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (order_id,order_no,merchant_id,merchant_name,product_id,product_name,price,quantity,total_amount,
                 pv_value,total_pv,cost_amount,total_cost,settlement_delay_days,team_bonus_mode)
                VALUES (?,?,?,?,1,'高风险商户商品',99,1,99,0,0,50,50,30,'NONE')
                """, orderId, "MO9966010", merchant.getId(), merchant.getMerchantName());

        merchantService.createOrderSettlements(orderId);
        DmsMerchantSettlement settlement = merchantService.listSettlements(merchant.getId(), "PENDING").get(0);
        assertEquals(30, settlement.getSettlementDelayDays());
        assertEquals(receivedAt.plusDays(30).withNano(0), settlement.getEligibleTime().withNano(0));
        assertEquals(0, merchantService.releaseEligibleSettlements(20));

        // 后续把商户默认周期改成0天，也不能改写历史订单已经锁定的30天。
        merchant.setDefaultSettlementDays(0);
        merchantService.updateMerchant(merchant.getId(), merchant);
        assertEquals(0, merchantService.releaseEligibleSettlements(20));

        long maturedOrderId = 9966011L;
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,merchant_id,merchant_name,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,status,pay_type,pay_time,receive_time,create_time)
                VALUES (?,?,?,?,?,1001,'测试会员','13800000000','测试地址',99,0,0,99,0,50,'NORMAL',3,'BALANCE',?,?,?)
                """, maturedOrderId, "MO9966011", 1L, merchant.getId(), merchant.getMerchantName(),
                LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(45));
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (order_id,order_no,merchant_id,merchant_name,product_id,product_name,price,quantity,total_amount,
                 pv_value,total_pv,cost_amount,total_cost,settlement_delay_days,team_bonus_mode)
                VALUES (?,?,?,?,1,'已到期风险商品',99,1,99,0,0,50,50,30,'NONE')
                """, maturedOrderId, "MO9966011", merchant.getId(), merchant.getMerchantName());
        merchantService.createOrderSettlements(maturedOrderId);
        assertEquals(1, merchantService.releaseEligibleSettlements(20));
        assertMoney("50.00", account(merchant.getId()).getAvailableAmount());
    }

    @Test
    void depositFreezeReleaseIsIndependentIdempotentAndOffsetsDebtFirst() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-DEPOSIT-FLOW");
        merchant.setMerchantName("保证金测试商户");
        merchant = merchantService.saveMerchant(merchant);
        Long merchantId = merchant.getId();
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=1000 WHERE merchant_id=?", merchantId);

        DmsAdminUser finance = new DmsAdminUser();
        finance.setId(92001L);
        finance.setUsername("finance-deposit");
        finance.setPermissions("finance:manage");
        AdminContext.set(finance);
        try {
            MerchantDepositAdjustDTO freeze = deposit(merchantId, "DEPOSIT-FREEZE-001", "300", "高风险品类履约保证金");
            DmsMerchantDepositFlow first = merchantService.freezeDeposit(freeze);
            DmsMerchantDepositFlow replay = merchantService.freezeDeposit(freeze);
            assertEquals(first.getId(), replay.getId());
            assertMoney("700.00", account(merchantId).getAvailableAmount());
            assertMoney("300.00", account(merchantId).getDepositFrozenAmount());
            assertMoney("0.00", account(merchantId).getFrozenAmount());

            MerchantDepositAdjustDTO changedReplay = deposit(merchantId, "DEPOSIT-FREEZE-001", "301", "错误复用请求号");
            assertThrows(RuntimeException.class, () -> merchantService.freezeDeposit(changedReplay));
            assertThrows(RuntimeException.class, () -> merchantService.freezeDeposit(
                    deposit(merchantId, "DEPOSIT-FREEZE-002", "701", "超过可提现余额")));

            jdbcTemplate.update("UPDATE dms_merchant_account SET debt_amount=100 WHERE merchant_id=?", merchantId);
            merchantService.releaseDeposit(deposit(merchantId, "DEPOSIT-RELEASE-001", "200", "风险期结束，释放部分保证金"));
            DmsMerchantAccount released = account(merchantId);
            assertMoney("800.00", released.getAvailableAmount());
            assertMoney("100.00", released.getDepositFrozenAmount());
            assertMoney("0.00", released.getDebtAmount());
            assertEquals(2, merchantService.listDepositFlows(merchantId).size());
            assertEquals(3, merchantService.listLedgers(merchantId, null).size());
        } finally {
            AdminContext.clear();
        }
    }

    @Test
    void merchantControlsStopNewPaymentButKeepHistoricalOrderWorkspaceSeparated() {
        DmsMerchant first = new DmsMerchant(); first.setMerchantNo("M-CONTROL-A"); first.setMerchantName("控制测试商户A");
        first = merchantService.saveMerchant(first);
        DmsMerchant second = new DmsMerchant(); second.setMerchantNo("M-CONTROL-B"); second.setMerchantName("控制测试商户B");
        second = merchantService.saveMerchant(second);
        long firstOrder = 9966091L; long secondOrder = 9966092L;
        insertMerchantOrder(firstOrder, "CONTROL-A-ORDER", first.getId(), first.getMerchantName(), 0);
        insertMerchantOrder(secondOrder, "CONTROL-B-ORDER", second.getId(), second.getMerchantName(), 1);

        MerchantControlDTO control = new MerchantControlDTO();
        control.setAccountStatus("ENABLED"); control.setBusinessStatus("SUSPENDED");
        control.setFulfillmentStatus("ENABLED"); control.setWithdrawalStatus("FROZEN");
        control.setSettlementStatus("FROZEN"); control.setDepositStatus("NORMAL");
        control.setAuditStatus("APPROVED"); control.setExitStatus("NORMAL");
        control.setReason("违规调查期间暂停新成交，保留历史订单履约");
        DmsMerchant frozen = merchantService.updateMerchantControls(first.getId(), control);
        assertEquals("ENABLED", frozen.getAccountStatus());
        assertEquals("SUSPENDED", frozen.getBusinessStatus());
        assertEquals("ENABLED", frozen.getFulfillmentStatus());
        assertThrows(RuntimeException.class, () -> merchantService.assertOrderCanBePaid(firstOrder));

        DmsAdminUser merchantAdmin = new DmsAdminUser();
        merchantAdmin.setId(96001L); merchantAdmin.setUsername("merchant-control-a");
        merchantAdmin.setMerchantId(first.getId()); merchantAdmin.setPermissions("shop:order,finance:read");
        AdminContext.set(merchantAdmin);
        try {
            assertEquals(List.of("CONTROL-A-ORDER"), shopService.listAdminOrders("CONTROL-", null, null).stream()
                    .map(row -> row.getOrder().getOrderNo()).toList());
            assertThrows(RuntimeException.class, () -> shopService.getOrder(secondOrder));
        } finally {
            AdminContext.clear();
        }

        control.setAuditStatus("REJECTED");
        control.setReason("准入复核驳回");
        merchantService.updateMerchantControls(first.getId(), control);
        assertTrue(merchantService.updateMerchantStatus(first.getId(), 1));
        DmsMerchant stillRejected = merchantService.listMerchants("M-CONTROL-A", null).get(0);
        assertEquals("REJECTED", stillRejected.getAuditStatus());
        assertEquals(0, stillRejected.getStatus());
    }

    @Test
    void merchantWorkspaceCanOnlyReadAndWithdrawItsOwnFunds() {
        DmsMerchant first = new DmsMerchant();
        first.setMerchantNo("M-WORKSPACE-ONE");
        first.setMerchantName("商户工作台一号");
        preparePayoutProfile(first);
        first = merchantService.saveMerchant(first);
        DmsMerchant second = new DmsMerchant();
        second.setMerchantNo("M-WORKSPACE-TWO");
        second.setMerchantName("商户工作台二号");
        second = merchantService.saveMerchant(second);
        Long firstId = first.getId();
        Long secondId = second.getId();
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id IN (?,?)", firstId, secondId);

        DmsAdminUser merchantAdmin = new DmsAdminUser();
        merchantAdmin.setId(93001L);
        merchantAdmin.setUsername("merchant-one");
        merchantAdmin.setMerchantId(firstId);
        merchantAdmin.setPermissions("admin:read,shop:product,finance:read,finance:manage");
        AdminContext.set(merchantAdmin);
        try {
            assertEquals(1, merchantService.listAccounts(null).size());
            assertEquals(firstId, merchantService.listAccounts(null).get(0).getMerchantId());
            assertTrue(merchantService.listSettlements(secondId, null).isEmpty());
            assertTrue(merchantService.listDepositFlows(secondId).isEmpty());

            MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
            apply.setRequestNo("WITHDRAW-MERCHANT-WORKSPACE");
            apply.setMerchantId(secondId);
            apply.setRequestedAmount(new BigDecimal("100"));
            DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);
            assertEquals(firstId, withdrawal.getMerchantId());
            assertMoney("400.00", merchantService.listAccounts(null).get(0).getAvailableAmount());
            assertThrows(RuntimeException.class, () -> merchantService.freezeDeposit(
                    deposit(firstId, "MERCHANT-CANNOT-FREEZE", "10", "商户不得自行冻结保证金")));
            MerchantWithdrawalReviewDTO review = new MerchantWithdrawalReviewDTO();
            review.setInvoiceStatus("NOT_REQUIRED");
            review.setAdjustmentAmount(BigDecimal.ZERO);
            assertThrows(RuntimeException.class, () -> merchantService.reviewWithdrawal(withdrawal.getId(), review));
        } finally {
            AdminContext.clear();
        }
    }

    @Test
    void merchantWorkspaceCannotReadAnotherMerchantsAfterSaleProofByGuessingItsPath() {
        DmsMerchant first = new DmsMerchant();
        first.setMerchantNo("M-PROOF-ONE"); first.setMerchantName("凭证隔离商户一");
        first = merchantService.saveMerchant(first);
        DmsMerchant second = new DmsMerchant();
        second.setMerchantNo("M-PROOF-TWO"); second.setMerchantName("凭证隔离商户二");
        second = merchantService.saveMerchant(second);

        long firstOrder = 9966401L;
        long secondOrder = 9966402L;
        insertMerchantOrder(firstOrder, "PROOF-ORDER-ONE", first.getId(), first.getMerchantName(), 2);
        insertMerchantOrder(secondOrder, "PROOF-ORDER-TWO", second.getId(), second.getMerchantName(), 2);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale(after_sale_no,order_id,order_no,member_id,user_id,proof_images,status)
                VALUES ('PROOF-AS-ONE',?,?,1001,1001,'[\"merchant-one-proof.png\"]',0),
                       ('PROOF-AS-TWO',?,?,1001,1001,'[\"merchant-two-proof.png\"]',0)
                """, firstOrder, "PROOF-ORDER-ONE", secondOrder, "PROOF-ORDER-TWO");

        DmsAdminUser merchantAdmin = new DmsAdminUser();
        merchantAdmin.setId(96501L); merchantAdmin.setUsername("merchant-proof-one");
        merchantAdmin.setMerchantId(first.getId()); merchantAdmin.setPermissions("shop:aftersale");
        AdminContext.set(merchantAdmin);
        try {
            assertDoesNotThrow(() -> shopAfterSaleService.assertAdminCanReadProof(1001L, "merchant-one-proof.png"));
            assertThrows(RuntimeException.class,
                    () -> shopAfterSaleService.assertAdminCanReadProof(1001L, "merchant-two-proof.png"));
        } finally {
            AdminContext.clear();
        }
    }

    @Test
    void withdrawalRequestIsDurablyIdempotentAndKeepsTheApprovedPayeeSnapshot() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-WITHDRAW-IDEMPOTENT");
        merchant.setMerchantName("提现防重复商户");
        preparePayoutProfile(merchant);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setRequestNo("WITHDRAW-DURABLE-REQUEST-001");
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("100"));
        DmsMerchantWithdrawal first = merchantService.applyWithdrawal(apply);
        DmsMerchantWithdrawal replay = merchantService.applyWithdrawal(apply);

        assertEquals(first.getId(), replay.getId());
        assertEquals("6222000000000000001", first.getBankAccountNoSnapshot());
        assertMoney("400.00", account(merchant.getId()).getAvailableAmount());
        assertMoney("100.00", account(merchant.getId()).getFrozenAmount());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dms_merchant_withdrawal WHERE request_no=?", Integer.class,
                "WITHDRAW-DURABLE-REQUEST-001"));

        merchant.setBankAccountNo("6222000000000000999");
        merchant.setContractStatus("EXPIRED");
        merchantService.updateMerchant(merchant.getId(), merchant);
        DmsMerchantWithdrawal afterProfileChange = merchantService.applyWithdrawal(apply);
        assertEquals(first.getId(), afterProfileChange.getId());
        assertEquals("6222000000000000001", afterProfileChange.getBankAccountNoSnapshot());

        apply.setRequestedAmount(new BigDecimal("90"));
        assertThrows(RuntimeException.class, () -> merchantService.applyWithdrawal(apply));
        assertMoney("100.00", account(merchant.getId()).getFrozenAmount());

        apply.setRequestedAmount(new BigDecimal("100"));
        apply.setRequestNo("WITHDRAW REQUEST WITH SPACES");
        assertThrows(RuntimeException.class, () -> merchantService.applyWithdrawal(apply));
        assertMoney("100.00", account(merchant.getId()).getFrozenAmount());
    }

    @Test
    void withdrawalExceptionalStatesPreserveFrozenMoneyAndCanResumeToCompletion() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-WITHDRAW-EXCEPTION");
        merchant.setMerchantName("提现异常流程商户");
        preparePayoutProfile(merchant);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=500 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setRequestNo("WITHDRAW-EXCEPTION-001");
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("200"));
        DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);

        MerchantWithdrawalActionDTO action = new MerchantWithdrawalActionDTO();
        action.setReason("命中人工风控规则，核对收款主体");
        assertEquals("RISK_FROZEN", merchantService.riskFreezeWithdrawal(withdrawal.getId(), action).getStatus());
        assertMoney("200.00", account(merchant.getId()).getFrozenAmount());
        action.setReason("主体核验通过");
        assertEquals("SUBMITTED", merchantService.resumeWithdrawal(withdrawal.getId(), action).getStatus());

        MerchantWithdrawalReviewDTO review = new MerchantWithdrawalReviewDTO();
        review.setInvoiceStatus("NOT_REQUIRED");
        review.setAdjustmentAmount(BigDecimal.ZERO);
        merchantService.reviewWithdrawal(withdrawal.getId(), review);
        assertEquals("PAYMENT_PROCESSING", merchantService.startWithdrawalPayment(withdrawal.getId()).getStatus());
        action.setReason("银行账户临时限制收款");
        assertEquals("PAYMENT_FAILED", merchantService.markWithdrawalPaymentFailed(withdrawal.getId(), action).getStatus());
        assertMoney("200.00", account(merchant.getId()).getFrozenAmount());
        assertEquals("PAYMENT_PROCESSING", merchantService.startWithdrawalPayment(withdrawal.getId()).getStatus());

        MerchantWithdrawalPayDTO pay = new MerchantWithdrawalPayDTO();
        pay.setActualPaidAmount(new BigDecimal("200"));
        pay.setPaymentReference("BANK-EXCEPTION-RETRY-001");
        assertEquals("PAID", merchantService.confirmPayment(withdrawal.getId(), pay).getStatus());
        assertEquals("COMPLETED", merchantService.completeWithdrawal(withdrawal.getId()).getStatus());
        assertMoney("300.00", account(merchant.getId()).getAvailableAmount());
        assertMoney("0.00", account(merchant.getId()).getFrozenAmount());
        assertMoney("200.00", account(merchant.getId()).getTotalPaidAmount());
        MerchantBalanceReconciliationVO reconciliation = merchantService.reconcileBalances(merchant.getId()).get(0);
        assertTrue(reconciliation.getLedgerInitialized());
        assertTrue(reconciliation.getConsistent());
    }

    @Test
    void merchantCanCancelOwnPendingWithdrawalOnlyOnce() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-WITHDRAW-CANCEL");
        merchant.setMerchantName("提现撤回商户");
        preparePayoutProfile(merchant);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=300 WHERE merchant_id=?", merchant.getId());

        MerchantWithdrawalApplyDTO apply = new MerchantWithdrawalApplyDTO();
        apply.setRequestNo("WITHDRAW-CANCEL-001");
        apply.setMerchantId(merchant.getId());
        apply.setRequestedAmount(new BigDecimal("100"));
        DmsMerchantWithdrawal withdrawal = merchantService.applyWithdrawal(apply);
        DmsAdminUser merchantUser = new DmsAdminUser();
        merchantUser.setId(97001L); merchantUser.setUsername("merchant-cancel"); merchantUser.setMerchantId(merchant.getId());
        AdminContext.set(merchantUser);
        try {
            MerchantWithdrawalActionDTO action = new MerchantWithdrawalActionDTO();
            action.setReason("本次暂不提现");
            assertEquals("CANCELED", merchantService.cancelWithdrawal(withdrawal.getId(), action).getStatus());
            assertThrows(RuntimeException.class, () -> merchantService.cancelWithdrawal(withdrawal.getId(), action));
        } finally {
            AdminContext.clear();
        }
        assertMoney("300.00", account(merchant.getId()).getAvailableAmount());
        assertMoney("0.00", account(merchant.getId()).getFrozenAmount());
    }

    @Test
    void finalMerchantExitIsBlockedUntilBusinessAndMoneyAreCleared() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-EXIT-CHECK");
        merchant.setMerchantName("退出检查商户");
        merchant = merchantService.saveMerchant(merchant);
        Long merchantId = merchant.getId();
        insertMerchantOrder(9966601L, "EXIT-CHECK-ORDER", merchantId, merchant.getMerchantName(), 1);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=88 WHERE merchant_id=?", merchantId);

        MerchantExitReadinessVO blocked = merchantService.getExitReadiness(merchantId);
        assertFalse(blocked.getReady());
        assertEquals(1, blocked.getUnfinishedOrderCount());
        assertTrue(blocked.getBlockers().stream().anyMatch(item -> item.contains("可提现余额")));

        MerchantControlDTO exit = new MerchantControlDTO();
        exit.setAccountStatus("DISABLED"); exit.setBusinessStatus("CLOSED"); exit.setFulfillmentStatus("DISABLED");
        exit.setWithdrawalStatus("FROZEN"); exit.setSettlementStatus("FROZEN"); exit.setDepositStatus("NORMAL");
        exit.setAuditStatus("APPROVED"); exit.setExitStatus("EXITED"); exit.setReason("退出条件验收完成");
        assertThrows(RuntimeException.class, () -> merchantService.updateMerchantControls(merchantId, exit));

        jdbcTemplate.update("DELETE FROM dms_shop_order WHERE id=9966601");
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=0 WHERE merchant_id=?", merchantId);
        assertTrue(merchantService.getExitReadiness(merchantId).getReady());
        assertEquals("EXITED", merchantService.updateMerchantControls(merchantId, exit).getExitStatus());
    }

    @Test
    void reconciliationDetectsOutOfLedgerBalanceMutation() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-RECONCILIATION");
        merchant.setMerchantName("账本对账商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=12.34 WHERE merchant_id=?", merchant.getId());
        MerchantBalanceReconciliationVO mismatch = merchantService.reconcileBalances(merchant.getId()).get(0);
        assertFalse(mismatch.getConsistent());
        assertMoney("12.34", mismatch.getAvailableDifference());
    }

    @Test
    void refundConsumesAvailableThenDepositBeforeCreatingMerchantDebt() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-DEPOSIT-REFUND");
        merchant.setMerchantName("保证金退款商户");
        merchant.setRequiredDepositAmount(new BigDecimal("100"));
        merchant = merchantService.saveMerchant(merchant);

        DmsAdminUser finance = new DmsAdminUser();
        finance.setId(94001L);
        finance.setUsername("deposit-receiver");
        finance.setPermissions("finance:manage");
        AdminContext.set(finance);
        try {
            merchantService.receiveDeposit(deposit(merchant.getId(), "DEPOSIT-RECEIVE-REFUND", "100", "线下保证金到账"));
        } finally {
            AdminContext.clear();
        }
        jdbcTemplate.update("UPDATE dms_merchant_account SET available_amount=30 WHERE merchant_id=?", merchant.getId());
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (order_id,order_no,merchant_id,merchant_name,product_id,product_name,price,quantity,total_amount,
                 pv_value,total_pv,cost_amount,total_cost,team_bonus_mode)
                VALUES (9977001,'MO9977001',?,?,1,'保证金退款商品',99,3,297,0,0,50,150,'NONE')
                """, merchant.getId(), merchant.getMerchantName());
        Long itemId = jdbcTemplate.queryForObject("SELECT id FROM dms_shop_order_item WHERE order_id=9977001", Long.class);
        jdbcTemplate.update("""
                INSERT INTO dms_merchant_settlement
                (tenant_id,merchant_id,order_id,order_no,order_item_id,product_id,quantity,refunded_quantity,
                 cost_amount,settlement_amount,reversed_amount,settlement_delay_days,status)
                VALUES (1,?,9977001,'MO9977001',?,1,3,0,50,150,0,0,'AVAILABLE')
                """, merchant.getId(), itemId);

        DmsShopAfterSaleItem refund = new DmsShopAfterSaleItem();
        refund.setOrderItemId(itemId);
        refund.setRefundQuantity(3);
        merchantService.reverseAfterSaleItems(List.of(refund));

        DmsMerchantAccount account = account(merchant.getId());
        assertMoney("0.00", account.getAvailableAmount());
        assertMoney("0.00", account.getDepositFrozenAmount());
        assertMoney("20.00", account.getDebtAmount());
    }

    @Test
    void settlementRuleChangeRequiresReviewAndMerchantDisableStopsSales() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-RULE-STATUS-CLOSURE");
        merchant.setMerchantName("结算规则与停用测试商户");
        merchant.setDefaultSettlementDays(7);
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("""
                UPDATE dms_shop_product
                SET merchant_id=?,merchant_name=?,status=1,normal_sale_enabled=1,
                    merchant_review_status='APPROVED',settlement_delay_days_override=NULL,team_bonus_mode='NONE'
                WHERE id=1
                """, merchant.getId(), merchant.getMerchantName());

        merchant.setDefaultSettlementDays(30);
        merchantService.updateMerchant(merchant.getId(), merchant);
        DmsShopProduct redrafted = shopService.getProduct(1L);
        assertEquals(0, redrafted.getStatus());
        assertEquals("DRAFT", redrafted.getMerchantReviewStatus());
        assertTrue(redrafted.getMerchantReviewRemark().contains("结算等待"));

        jdbcTemplate.update("UPDATE dms_shop_product SET status=1,merchant_review_status='APPROVED' WHERE id=1");
        assertTrue(merchantService.updateMerchantStatus(merchant.getId(), 0));
        assertEquals(0, shopService.getProduct(1L).getStatus());
        assertTrue(shopService.listProducts(1L, null, null, null, null).stream()
                .noneMatch(product -> product.getId().equals(1L)));
        assertThrows(RuntimeException.class, () -> shopService.updateProductStatus(1L, 1));
    }

    private MerchantDepositAdjustDTO deposit(Long merchantId, String operationNo, String amount, String reason) {
        MerchantDepositAdjustDTO dto = new MerchantDepositAdjustDTO();
        dto.setMerchantId(merchantId);
        dto.setOperationNo(operationNo);
        dto.setAmount(new BigDecimal(amount));
        dto.setReason(reason);
        return dto;
    }

    private void preparePayoutProfile(DmsMerchant merchant) {
        merchant.setLegalEntityName(merchant.getMerchantName());
        merchant.setUnifiedSocialCreditCode("91430100TEST000001");
        merchant.setBankAccountName(merchant.getMerchantName());
        merchant.setBankName("测试银行长沙支行");
        merchant.setBankAccountNo("6222000000000000001");
        merchant.setInvoiceTitle(merchant.getMerchantName());
        merchant.setTaxpayerIdentificationNo("91430100TEST000001");
        merchant.setContractStatus("SIGNED");
    }

    private DmsMerchantAccount account(Long merchantId) {
        return merchantService.listAccounts(null).stream()
                .filter(item -> merchantId.equals(item.getMerchantId())).findFirst().orElseThrow();
    }

    private void insertMerchantOrder(long id, String orderNo, Long merchantId, String merchantName, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,merchant_id,merchant_name,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,status)
                VALUES (?,?,?,?,?,1001,'测试会员','13800000000','测试地址',100,0,0,100,0,50,'NORMAL',?)
                """, id, orderNo, 1L, merchantId, merchantName, status);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
