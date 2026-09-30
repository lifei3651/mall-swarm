package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 使用隔离数据库和真实服务验证支付、归属、佣金、券及退款；不触发外部渠道交易。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:direct_referral_lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class DirectReferralLifecycleIntegrationTest {
    @Autowired private JdbcTemplate db;
    @Autowired private SqlSessionTemplate session;
    @Autowired private ShopService shop;
    @Autowired private ShopWalletService wallet;
    @Autowired private MemberAssetService assets;
    @Autowired private ShopAfterSaleService afterSales;
    @Autowired private CommissionService commissions;
    @Autowired private ShopCouponService coupons;
    @Autowired private MerchantService merchants;
    @Autowired private DirectReferralConfigService config;
    @Autowired private DmsShopMemberDao members;
    @Autowired private DmsAgentDao agents;
    @Autowired private DmsCommissionRecordDao records;
    @Autowired private DmsOrderRelationSnapshotDao snapshots;
    @Autowired private DmsMemberFirstPaymentDao firstPayments;
    @MockitoBean private SmsVerificationService sms;

    private DmsShopMember buyer;

    @BeforeEach
    void prepare() {
        TenantContext.setTenantId(1L);
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(1L); admin.setUsername("admin"); admin.setNickname("隔离验收管理员");
        admin.setRoleCode("SUPER_ADMIN"); admin.setPermissions("*"); admin.setStatus(1);
        AdminContext.set(admin);
        db.update("UPDATE dms_tenant SET invitation_enabled=1,promotion_join_mode='MANUAL_REVIEW',coupon_enabled=1,balance_transactions_enabled=1,multi_merchant_enabled=1 WHERE id=1");
        db.update("UPDATE dms_shop_product SET team_bonus_mode='INHERIT' WHERE id IN(1,2)");
        member(1001L, "18800000001", null, "合格邀请人A");
        buyer = member(994501L, "18899450101", 1001L, "普通客户B");
        systemAccount(9001L, -9001L, "SYSTEM_REMAINDER", "SYSR0001");
        systemAccount(9002L, -9002L, "SYSTEM_PRODUCT_COST", "SYSC0001");
        issueBalance(buyer);
        session.clearCache();
    }

    @AfterEach
    void clear() { AdminContext.clear(); TenantContext.clear(); }

    @Test
    void ordinaryBuyerPaysWithoutQualificationAndAReceivesFrozenPendingCommissionOnce() {
        Long versionId = activate("ALL_ORDERS", "0.10");
        ShopOrderVO order = paid(buyer, null, item(1L, 1L));
        assertNull(agents.selectByUserId(buyer.getUserId()), "购物不能隐式开通推广资格");
        assertEquals(0, members.selectByUserId(buyer.getUserId()).getTeamOptIn());
        DmsCommissionRecord record = onlyCommission(order);
        assertEquals("DIRECT_REFERRAL", record.getBonusType());
        assertEquals(1L, record.getAgentId());
        assertEquals(versionId, record.getRuleVersionId());
        assertEquals(0, record.getStatus());
        money("299.00", record.getOrderAmount()); money("29.90", record.getCommissionAmount());
        var anchor = snapshots.selectByOrderId(order.getOrder().getId()).stream()
                .filter(row -> Integer.valueOf(0).equals(row.getRelationLevel())).findFirst().orElseThrow();
        assertNull(anchor.getOwnerAgentId()); assertNull(anchor.getTargetAgentId());
        assertEquals(buyer.getUserId(), anchor.getTargetUserId());

        // 改规则及关系后，旧订单重复支付/计算保持原归属与原金额。
        activate("ALL_ORDERS", "0.20");
        db.update("UPDATE dms_shop_member SET inviter_id=NULL WHERE id=?", buyer.getId()); session.clearCache();
        shop.markOrderPaid(order.getOrder().getId(), "BALANCE");
        commissions.calculateAndRecordCommission(1L, order.getOrder().getId(), order.getOrder().getOrderNo(),
                new BigDecimal("299"), buyer.getUserId(), buyer.getNickname());
        assertEquals(1, records.selectByOrderId(order.getOrder().getId()).size());
        money("29.90", onlyCommission(order).getCommissionAmount());
        assertEquals(versionId, onlyCommission(order).getRuleVersionId());
    }

    @Test
    void ineligibleInviterGetsNoCommissionAndLaterQualificationDoesNotBackfillTheOldOrder() {
        activate("ALL_ORDERS", "0.10");
        db.update("UPDATE dms_agent SET status=0 WHERE id=1"); session.clearCache();
        ShopOrderVO order = paid(buyer, null, item(1L, 1L));
        assertTrue(records.selectByOrderId(order.getOrder().getId()).isEmpty());
        assertEquals(1, snapshots.selectByOrderId(order.getOrder().getId()).size());
        db.update("UPDATE dms_agent SET status=1 WHERE id=1"); session.clearCache();
        commissions.calculateAndRecordCommission(1L, order.getOrder().getId(), order.getOrder().getOrderNo(),
                new BigDecimal("299"), buyer.getUserId(), buyer.getNickname());
        assertTrue(records.selectByOrderId(order.getOrder().getId()).isEmpty());
    }

    @Test
    void cPurchaseDoesNotBypassUnqualifiedBToQualifiedA() {
        activate("ALL_ORDERS", "0.10");
        DmsShopMember customerC = member(994502L, "18899450202", buyer.getUserId(), "普通客户C");
        issueBalance(customerC);
        ShopOrderVO created = paid(customerC, null, item(1L, 1L));
        assertNull(agents.selectByUserId(buyer.getUserId()));
        assertNull(agents.selectByUserId(customerC.getUserId()));
        assertTrue(records.selectByOrderId(created.getOrder().getId()).isEmpty());
        assertTrue(snapshots.selectByOrderId(created.getOrder().getId()).stream()
                .noneMatch(row -> Integer.valueOf(1).equals(row.getRelationLevel())));
    }

    @Test
    void firstPaymentScopeIncludesAllTradeChildrenAndRefundDoesNotResetIt() {
        activate("FIRST_PAID_ORDER", "0.10");
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("DR-LIFECYCLE-MERCHANT"); merchant.setMerchantName("首笔交易子单商户");
        merchant = merchants.saveMerchant(merchant);
        Long persistedMerchantId = merchant.getId();
        db.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=? WHERE id=2", merchant.getId(), merchant.getMerchantName());
        session.clearCache();
        ShopOrderVO submitted = shop.submitOrder(submit(null, item(1L, 1L), item(2L, 3L)), buyer);
        assertTrue(submitted.getGroupedCheckout());
        ShopOrderVO merchantChild = submitted.getChildOrders().stream()
                .filter(child -> persistedMerchantId.equals(child.getOrder().getMerchantId())).findFirst().orElseThrow();
        assertEquals("NONE", merchantChild.getItems().get(0).getTeamBonusMode(),
                "新商户商品仍保持不参与奖金，不能为测试改变现行业务规则");
        // 仅构造已落单的历史 STANDARD 快照，以验证同一首笔交易的两个子单；不开放新商户商品计佣。
        assertEquals(1, db.update("UPDATE dms_shop_order_item SET team_bonus_mode='STANDARD' WHERE order_id=? AND product_id=2",
                merchantChild.getOrder().getId()));
        session.clearCache();
        ShopOrderVO grouped = payCreated(buyer, submitted);
        assertTrue(grouped.getGroupedCheckout()); assertEquals(2, grouped.getChildOrders().size());
        Long marker = firstPayments.selectFirstOrderForUpdate(1L, buyer.getUserId());
        assertNotNull(marker);
        BigDecimal total = BigDecimal.ZERO;
        for (ShopOrderVO child : grouped.getChildOrders()) {
            total = total.add(onlyCommission(child).getCommissionAmount());
            assertTrue(snapshots.selectByOrderId(child.getOrder().getId()).stream()
                    .allMatch(row -> Integer.valueOf(1).equals(row.getFirstPaidOrderEligible())));
            assertTrue(afterSales.cancelPendingShipment(child.getOrder().getId(), 1L, "隔离退款验收"));
            assertEquals(3, onlyCommission(child).getStatus());
        }
        money("49.70", total);
        assertEquals(marker, firstPayments.selectFirstOrderForUpdate(1L, buyer.getUserId()));
        ShopOrderVO next = paid(buyer, null, item(1L, 1L));
        assertTrue(records.selectByOrderId(next.getOrder().getId()).isEmpty());
        assertTrue(snapshots.selectByOrderId(next.getOrder().getId()).stream()
                .allMatch(row -> Integer.valueOf(0).equals(row.getFirstPaidOrderEligible())));
    }

    @Test
    void allNoneProductsProduceNoCommissionAndDoNotTurnBuyerIntoAMember() {
        activate("ALL_ORDERS", "0.10");
        db.update("UPDATE dms_shop_product SET team_bonus_mode='NONE' WHERE id=1"); session.clearCache();
        ShopOrderVO order = paid(buyer, null, item(1L, 1L));
        assertTrue(records.selectByOrderId(order.getOrder().getId()).isEmpty());
        assertNull(agents.selectByUserId(buyer.getUserId()));
    }

    @Test
    void platformCouponAndExcludedProductAreRemovedFromActualCashCommissionBase() {
        activate("ALL_ORDERS", "0.10");
        db.update("UPDATE dms_shop_product SET team_bonus_mode='NONE' WHERE id=2"); session.clearCache();
        ShopCouponSaveDTO coupon = new ShopCouponSaveDTO();
        coupon.setTitle("平台承担优惠"); coupon.setScopeType("PRODUCTS"); coupon.setProductIds(List.of(1L));
        coupon.setBusinessTypes(List.of("NORMAL")); coupon.setAmount(new BigDecimal("10"));
        coupon.setMinimumAmount(new BigDecimal("100")); coupon.setMerchantPercent(0);
        // 历史券规则GROSS不能改变基础直接推荐的实际现金口径。
        coupon.setBonusBasis("GROSS"); coupon.setRefundRule("FULL_RETURN");
        coupon.setStartsAt(LocalDateTime.now().minusHours(1)); coupon.setEndsAt(LocalDateTime.now().plusDays(3));
        coupon.setTotalCount(10); coupon.setPerMemberLimit(1); coupon.setVersion(0); coupon.setImpactConfirmed(true);
        DmsShopCoupon draft = coupons.save(null, coupon);
        DmsShopCoupon published = coupons.status(draft.getId(), 0, "PUBLISHED", true);
        Long claimId = coupons.claim(buyer, published.getId(), "direct-referral-coupon-claim-001").getClaimId();
        ShopOrderVO order = paid(buyer, claimId, item(1L, 1L), item(2L, 3L));
        DmsCommissionRecord record = onlyCommission(order);
        money("487.00", order.getOrder().getPayAmount());
        money("289.00", record.getOrderAmount()); money("28.90", record.getCommissionAmount());
        DmsShopOrderItem excluded = order.getItems().stream().filter(item -> item.getProductId().equals(2L)).findFirst().orElseThrow();
        assertEquals("NONE", excluded.getTeamBonusMode());
        assertEquals(new BigDecimal("0.00"), excluded.getCouponDiscountAmount());
    }

    private Long activate(String scope, String rate) {
        var current = config.current();
        DirectReferralConfigDTO dto = new DirectReferralConfigDTO();
        dto.setEnabled(true); dto.setCommissionRate(new BigDecimal(rate));
        dto.setPurchaseScope(scope); dto.setSettlementDelayDays(7);
        dto.setExpectedVersionId(current.getVersionId()); dto.setConfirmPolicySwitch(true);
        return config.save(dto).getVersionId();
    }

    private ShopOrderVO paid(DmsShopMember member, Long coupon, ShopOrderItemDTO... items) {
        ShopOrderVO created = shop.submitOrder(submit(coupon, items), member);
        return payCreated(member, created);
    }

    private ShopOrderVO payCreated(DmsShopMember member, ShopOrderVO created) {
        BalancePayDTO pay = new BalancePayDTO(); pay.setPaymentPassword("864209");
        return wallet.payOrder(member, created.getOrder().getId(), pay);
    }

    private ShopOrderSubmitDTO submit(Long coupon, ShopOrderItemDTO... items) {
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setReceiverName("隔离收货人"); dto.setReceiverPhone("18899450101");
        dto.setReceiverProvince("湖南省"); dto.setReceiverCity("长沙市"); dto.setReceiverDistrict("岳麓区");
        dto.setReceiverDetailAddress("验收路1号"); dto.setReceiverAddress("湖南省长沙市岳麓区验收路1号");
        dto.setPayType("BALANCE"); dto.setBusinessType("NORMAL"); dto.setCouponClaimId(coupon);
        dto.setItems(List.of(items)); return dto;
    }

    private ShopOrderItemDTO item(Long productId, Long skuId) {
        ShopOrderItemDTO dto = new ShopOrderItemDTO(); dto.setProductId(productId); dto.setSkuId(skuId); dto.setQuantity(1);
        return dto;
    }

    private DmsShopMember member(Long userId, String phone, Long inviter, String nickname) {
        db.update("""
                INSERT INTO dms_shop_member
                  (user_id,phone,login_account,password_hash,pay_password_hash,nickname,inviter_id,status,system_account,team_opt_in)
                VALUES (?,?,?,'test-fixture',?,?,?,1,0,0)
                """, userId, phone, "DR" + userId, BCrypt.hashpw("864209"), nickname, inviter);
        return members.selectByUserId(userId);
    }

    private void issueBalance(DmsShopMember member) {
        AssetChangeDTO balance = new AssetChangeDTO();
        balance.setUserId(member.getUserId()); balance.setAmount(new BigDecimal("4000.00"));
        balance.setBizType("DIRECT_REFERRAL_ACCEPTANCE"); balance.setBizId("DR-INITIAL-" + member.getUserId());
        balance.setRequestId(UUID.randomUUID().toString()); balance.setRemark("隔离余额支付验收资金");
        assets.issue(balance);
    }

    private void systemAccount(Long id, Long userId, String account, String code) {
        db.update("""
                INSERT INTO dms_shop_member
                  (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (?,?,?,?,'disabled-system-account',?,?,0,1,0)
                """, id, userId, "SYS" + Math.abs(userId), account, account, code);
        db.update("""
                INSERT INTO dms_agent (id,user_id,agent_code,agent_name,agent_level,level_depth,invite_code,status,source_type)
                VALUES (?,?,?,?,1,1,?,2,3)
                """, id, userId, account, account, code);
    }

    private DmsCommissionRecord onlyCommission(ShopOrderVO order) {
        var rows = records.selectByOrderId(order.getOrder().getId()); assertEquals(1, rows.size()); return rows.get(0);
    }

    private void money(String value, BigDecimal actual) { assertEquals(0, new BigDecimal(value).compareTo(actual)); }
}
