package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.bonus.CustomerBonusOrderContext;
import com.macro.mall.distribution.bonus.CustomerBonusPayout;
import com.macro.mall.distribution.bonus.CustomerBonusPolicy;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.bonus.CustomerBonusRefundContext;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberRealNameDao;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.PaymentPasswordDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleItemDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberRealName;
import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.enums.WithdrawStatusEnum;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 正式交付候选的业务验收门禁。
 *
 * <p>本用例只使用隔离 H2 数据，按真实服务顺序覆盖：公开注册、团队 H5 绑定、
 * 余额支付、发货、客服备注、收货、奖金结算、提现，以及退货退款和库存回补。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery_stability_acceptance;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisConfig.class,
        ScheduleTask.class
}))
@Import(DeliveryStabilityAcceptanceTest.CustomerPolicyFixtureConfig.class)
class DeliveryStabilityAcceptanceTest {

    private static final String LOGIN_PASSWORD = "Delivery123";
    private static final String PAYMENT_PASSWORD = "864209";
    private static final String INVITER_PASSWORD = "Inviter123";

    @Autowired private ShopAuthService authService;
    @Autowired private ShopWalletService walletService;
    @Autowired private ShopService shopService;
    @Autowired private ShopAfterSaleService afterSaleService;
    @Autowired private CommissionSettlementService settlementService;
    @Autowired private OrderBalanceAllocationService orderBalanceAllocationService;
    @Autowired private MemberAssetService memberAssetService;
    @Autowired private WithdrawService withdrawService;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private DmsShopOrderItemDao orderItemDao;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private DmsCommissionRecordDao commissionDao;
    @Autowired private DmsOrderRelationSnapshotDao relationSnapshotDao;
    @Autowired private DmsMemberAssetAccountDao assetAccountDao;
    @Autowired private DmsMemberRealNameDao realNameDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private TestCustomerBonusPolicy customerBonusPolicy;
    @MockitoBean private SmsVerificationService smsVerificationService;
    @MockitoBean private LoginCaptchaService loginCaptchaService;

    @Test
    void registrationToRefundBonusAndWithdrawalRemainConsistent() {
        prepareSystemFundAccounts();
        DmsShopMember inviter = prepareInviter();
        DmsShopMember member = registerAndJoinTeam();
        setPaymentPassword(member, LOGIN_PASSWORD, PAYMENT_PASSWORD);
        issueBalance(member.getUserId(), "1000.00", "交付验收初始余额");

        int initialProductStock = productDao.selectById(1L).getStock();
        BigDecimal initialBalance = balance(member.getUserId());

        // 第一单完整履约并结算奖金，验证团队关系、奖金冷静期和奖金提现。
        ShopOrderVO first = submitAndPay(member);
        assertMoney("701.00", balance(member.getUserId()));
        shipAndReceive(first.getOrder().getId(), member, "SF-ACCEPT-001");
        assertTrue(shopService.updateOrderServiceRemark(first.getOrder().getId(), "已核对地址，优先处理"));
        assertEquals("已核对地址，优先处理", orderDao.selectById(first.getOrder().getId()).getServiceRemark());

        List<DmsCommissionRecord> firstCommissions = commissionDao.selectByOrderId(first.getOrder().getId());
        assertFalse(firstCommissions.isEmpty(), "加入团队后首笔有效订单必须生成奖金快照");
        assertTrue(firstCommissions.stream().allMatch(item -> Integer.valueOf(0).equals(item.getStatus())));

        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_mode='RECEIVED', after_sale_window_days=0 WHERE id=1");
        jdbcTemplate.update("UPDATE dms_shop_order SET receive_time=? WHERE id=?",
                LocalDateTime.now().minusMinutes(1), first.getOrder().getId());
        sqlSessionTemplate.clearCache();
        assertTrue(settlementService.settleEligibleAfterCoolingOff(100) > 0);
        assertEquals(2, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100),
                "产品成本和剩余商品款必须在售后期结束后各归集一次");
        assertEquals(0, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100),
                "重复执行资金归集不能重复入账");
        assertTrue(commissionDao.selectByOrderId(first.getOrder().getId()).stream()
                .allMatch(item -> Integer.valueOf(1).equals(item.getStatus())));
        assertTrue(balance(inviter.getUserId()).compareTo(BigDecimal.ZERO) > 0,
                "结算奖金必须进入邀请人统一钱包");

        setPaymentPassword(inviter, INVITER_PASSWORD, "975310");
        BigDecimal inviterBeforeWithdrawal = balance(inviter.getUserId());
        BigDecimal withdrawAmount = inviterBeforeWithdrawal.min(new BigDecimal("10.00"));
        WithdrawRecordVO withdrawal = walletService.applyWithdrawal(inviter,
                withdrawal("975310", withdrawAmount));
        assertEquals(WithdrawStatusEnum.PENDING_AUDIT.getValue(), withdrawal.getStatus());
        assertMoney(inviterBeforeWithdrawal.subtract(withdrawAmount), balance(inviter.getUserId()));
        WithdrawAuditDTO approve = new WithdrawAuditDTO();
        approve.setId(withdrawal.getId());
        approve.setStatus(WithdrawStatusEnum.AUDIT_PASSED.getValue());
        approve.setAuditUserId(1L);
        approve.setAuditUserName("交付验收财务");
        approve.setAuditRemark("奖金提现核对通过");
        assertTrue(withdrawService.auditWithdraw(approve));
        assertTrue(withdrawService.confirmPay(withdrawal.getId(), "BANK-ACCEPT-001"));
        assertEquals(WithdrawStatusEnum.PAY_SUCCESS.getValue(),
                withdrawService.getWithdrawById(withdrawal.getId()).getStatus());

        // 第二单走退货退款：余额原路退回、奖金冲销、库存只回补一次。
        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_mode='RECEIVED', after_sale_window_days=7 WHERE id=1");
        prepareDefaultReturnAddress();
        BigDecimal beforeRefundOrder = balance(member.getUserId());
        ShopOrderVO refundable = submitAndPay(member);
        shipAndReceive(refundable.getOrder().getId(), member, "SF-ACCEPT-002");
        Long orderItemId = orderItemDao.selectByOrderId(refundable.getOrder().getId()).get(0).getId();

        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(refundable.getOrder().getId());
        apply.setApplyType(2);
        apply.setReason("交付验收退货退款");
        ShopAfterSaleItemDTO refundItem = new ShopAfterSaleItemDTO();
        refundItem.setOrderItemId(orderItemId);
        refundItem.setQuantity(1);
        apply.setItems(List.of(refundItem));
        DmsShopAfterSale afterSale = afterSaleService.apply(member, apply);
        assertEquals(0, afterSale.getStatus());

        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditUserId(1L);
        audit.setAuditUserName("交付验收客服");
        audit.setAuditRemark("同意退货，等待寄回");
        afterSale = afterSaleService.audit(afterSale.getId(), audit);
        assertEquals(4, afterSale.getStatus());

        ShopAfterSaleReturnShipmentDTO returnShipment = new ShopAfterSaleReturnShipmentDTO();
        returnShipment.setDeliveryCompany("顺丰速运");
        returnShipment.setDeliveryNo("SFRETURN001");
        afterSale = afterSaleService.submitReturnShipment(member, afterSale.getId(), returnShipment);
        assertEquals(5, afterSale.getStatus());
        LocalDateTime firstReturnShippedAt = afterSale.getReturnShippedAt();
        DmsShopAfterSale repeatedShipment = afterSaleService.submitReturnShipment(member, afterSale.getId(), returnShipment);
        assertEquals(firstReturnShippedAt, repeatedShipment.getReturnShippedAt(),
                "同一退货物流重复提交必须幂等，不能改写首次寄回时间");
        afterSale = afterSaleService.confirmReturnReceived(afterSale.getId(), audit);
        assertEquals(1, afterSale.getStatus());
        assertEquals(4, orderDao.selectById(refundable.getOrder().getId()).getStatus());
        assertMoney(beforeRefundOrder, balance(member.getUserId()));
        int stockAfterRefund = productDao.selectById(1L).getStock();
        DmsShopAfterSale repeatedConfirmation = afterSaleService.confirmReturnReceived(afterSale.getId(), audit);
        assertEquals(1, repeatedConfirmation.getStatus());
        assertMoney(beforeRefundOrder, balance(member.getUserId()));
        assertEquals(stockAfterRefund, productDao.selectById(1L).getStock(),
                "重复确认收货不能再次退款或回补库存");
        List<DmsOrderBalanceAllocation> refundedAllocations =
                orderBalanceAllocationService.listByOrderId(refundable.getOrder().getId());
        assertEquals(2, refundedAllocations.size());
        assertTrue(refundedAllocations.stream().allMatch(row -> Integer.valueOf(2).equals(row.getStatus())));
        assertTrue(refundedAllocations.stream().allMatch(row ->
                row.getCurrentAmount().compareTo(BigDecimal.ZERO) == 0));
        assertEquals(initialProductStock - 1, productDao.selectById(1L).getStock(),
                "第一单保留销量，第二单退货只回补一次库存");

        // 客户派生项目可接入独立制度；即使支付后切换成其他制度，退款仍回调原制度。
        customerBonusPolicy.reset();
        Long customVersionId = activatePolicy(TestCustomerBonusPolicy.POLICY_CODE, "验收客户独立制度");
        BigDecimal beforeCustomOrder = balance(member.getUserId());
        ShopOrderVO customOrder = submitAndPay(member);
        List<DmsCommissionRecord> customCommissions = commissionDao.selectByOrderId(customOrder.getOrder().getId());
        assertEquals(1, customCommissions.size());
        assertEquals(TestCustomerBonusPolicy.BONUS_CODE, customCommissions.get(0).getBonusType());
        assertEquals(customVersionId, customCommissions.get(0).getRuleVersionId());
        assertMoney("14.95", customCommissions.get(0).getCommissionAmount());
        assertEquals(1, customerBonusPolicy.orderEvents());

        Long disabledVersionId = activatePolicy(CustomerBonusPolicyCodes.DISABLED, "新客户默认无奖金");
        returnAndRefund(member, customOrder, "SF-ACCEPT-003", "SFRETURN003", "客户制度切换退款验收");
        DmsCommissionRecord refundedCustomCommission = commissionDao
                .selectByOrderId(customOrder.getOrder().getId()).get(0);
        assertEquals(3, refundedCustomCommission.getStatus());
        assertMoney("0.00", refundedCustomCommission.getCommissionAmount());
        assertMoney(beforeCustomOrder, balance(member.getUserId()));
        assertEquals(1, customerBonusPolicy.refundEvents(),
                "退款必须回调订单支付时冻结的客户制度，而不是后来切换的制度");

        // 新客户默认无奖金，但注册、下单、支付、履约、售后和退款仍完整可用。
        BigDecimal beforeDisabledOrder = balance(member.getUserId());
        ShopOrderVO disabledOrder = submitAndPay(member);
        assertTrue(commissionDao.selectByOrderId(disabledOrder.getOrder().getId()).isEmpty());
        var disabledSnapshots = relationSnapshotDao.selectByOrderId(disabledOrder.getOrder().getId());
        assertFalse(disabledSnapshots.isEmpty());
        assertTrue(disabledSnapshots.stream()
                .allMatch(item -> disabledVersionId.equals(item.getRuleVersionId())));
        returnAndRefund(member, disabledOrder, "SF-ACCEPT-004", "SFRETURN004", "默认无奖金全流程验收");
        assertMoney(beforeDisabledOrder, balance(member.getUserId()));
        assertTrue(commissionDao.selectByOrderId(disabledOrder.getOrder().getId()).isEmpty());
        assertEquals(1, customerBonusPolicy.refundEvents(),
                "默认无奖金订单退款不能误调用其他客户制度");
        assertEquals(initialProductStock - 1, productDao.selectById(1L).getStock(),
                "两笔扩展验收订单退款后也必须各自只回补一次库存");

        // 流水守恒：所有退款单回到支付前余额，只保留第一单有效消费。
        BigDecimal memberEffectiveOut = first.getOrder().getPayAmount();
        assertMoney(initialBalance.subtract(memberEffectiveOut), balance(member.getUserId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dms_finance_refund WHERE order_id=?",
                Integer.class, refundable.getOrder().getId()));
    }

    private Long activatePolicy(String policyCode, String policyName) {
        jdbcTemplate.update("UPDATE dms_commission_rule_version SET status=0 WHERE tenant_id=1");
        jdbcTemplate.update("""
                INSERT INTO dms_commission_rule_version
                  (tenant_id,version_no,version_name,status,effective_time,remark)
                VALUES (1,?,?,1,CURRENT_TIMESTAMP,'交付验收动态切换')
                """, policyCode, policyName);
        sqlSessionTemplate.clearCache();
        return jdbcTemplate.queryForObject("""
                SELECT id FROM dms_commission_rule_version
                WHERE tenant_id=1 AND status=1 ORDER BY id DESC LIMIT 1
                """, Long.class);
    }

    private DmsShopAfterSale returnAndRefund(DmsShopMember member, ShopOrderVO order,
                                             String deliveryNo, String returnDeliveryNo, String reason) {
        shipAndReceive(order.getOrder().getId(), member, deliveryNo);
        Long orderItemId = orderItemDao.selectByOrderId(order.getOrder().getId()).get(0).getId();
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(order.getOrder().getId());
        apply.setApplyType(2);
        apply.setReason(reason);
        ShopAfterSaleItemDTO item = new ShopAfterSaleItemDTO();
        item.setOrderItemId(orderItemId);
        item.setQuantity(1);
        apply.setItems(List.of(item));
        DmsShopAfterSale afterSale = afterSaleService.apply(member, apply);
        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditUserId(1L);
        audit.setAuditUserName("交付验收客服");
        audit.setAuditRemark("同意退货，等待寄回");
        afterSale = afterSaleService.audit(afterSale.getId(), audit);
        ShopAfterSaleReturnShipmentDTO shipment = new ShopAfterSaleReturnShipmentDTO();
        shipment.setDeliveryCompany("顺丰速运");
        shipment.setDeliveryNo(returnDeliveryNo);
        afterSaleService.submitReturnShipment(member, afterSale.getId(), shipment);
        DmsShopAfterSale completed = afterSaleService.confirmReturnReceived(afterSale.getId(), audit);
        assertEquals(1, completed.getStatus());
        assertEquals(4, orderDao.selectById(order.getOrder().getId()).getStatus());
        return completed;
    }

    private void prepareSystemFundAccounts() {
        insertSystemFundAccount(9001L, -9001L, "SYSTEM_REMAINDER", "SYSR0001", "剩余商品款账户");
        insertSystemFundAccount(9002L, -9002L, "SYSTEM_PRODUCT_COST", "SYSC0001", "产品成本账户");
    }

    private void insertSystemFundAccount(long id, long userId, String account,
                                         String inviteCode, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                  (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (?,?,?,?,'disabled-system-account',?,?,0,1,0)
                """, id, userId, "SYS" + Math.abs(userId), account, nickname, inviteCode);
        jdbcTemplate.update("""
                INSERT INTO dms_agent
                  (id,user_id,agent_code,agent_name,agent_level,level_depth,invite_code,status,source_type)
                VALUES (?,?,?,?,1,1,?,2,3)
                """, id, userId, account, nickname, inviteCode);
    }

    private DmsShopMember prepareInviter() {
        jdbcTemplate.update("UPDATE dms_agent SET invite_code='INV00001' WHERE id=1");
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                  (user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (1001,'18800000001','inviter01',?,'验收邀请人','INV00001',1,0,1)
                """, BCrypt.hashpw(INVITER_PASSWORD));
        DmsShopMember inviter = memberDao.selectByUserId(1001L);
        DmsMemberRealName realName = new DmsMemberRealName();
        realName.setTenantId(1L);
        realName.setMemberId(inviter.getId());
        realName.setUserId(inviter.getUserId());
        realName.setStatus(1);
        realName.setRealName("验收邀请人");
        realName.setIdCard("11010519491231002X");
        realName.setProvider("TEST");
        realName.setConsentVersion("TEST_V1");
        realName.setConsentTime(LocalDateTime.now());
        realName.setVerifiedTime(LocalDateTime.now());
        realNameDao.insert(realName);
        return inviter;
    }

    private DmsShopMember registerAndJoinTeam() {
        ShopRegisterDTO register = new ShopRegisterDTO();
        register.setPhone("18800000088");
        register.setUsername("delivery88");
        register.setPassword(LOGIN_PASSWORD);
        register.setNickname("交付验收会员");
        register.setSmsCode("123456");
        register.setCaptchaId("delivery-captcha-id");
        register.setCaptchaCode("A1B2");
        register.setInviteCode("INV00001");
        ShopAuthVO auth = authService.registerPublic(register);
        assertNotNull(auth.getToken());
        DmsShopMember member = memberDao.selectById(auth.getMember().getId());
        assertEquals(1, member.getTeamOptIn());
        assertEquals(1001L, member.getInviterId());
        return member;
    }

    private void setPaymentPassword(DmsShopMember member, String loginPassword, String paymentPassword) {
        PaymentPasswordDTO dto = new PaymentPasswordDTO();
        dto.setNewPassword(paymentPassword);
        dto.setLoginPassword(loginPassword);
        dto.setSmsCode("123456");
        assertTrue(walletService.setPaymentPassword(member, dto));
    }

    private void issueBalance(Long userId, String amount, String remark) {
        AssetChangeDTO dto = new AssetChangeDTO();
        dto.setUserId(userId);
        dto.setAmount(new BigDecimal(amount));
        dto.setBizType("DELIVERY_ACCEPTANCE_ISSUE");
        dto.setBizId("DELIVERY-ACCEPTANCE-" + userId);
        dto.setRequestId(UUID.randomUUID().toString());
        dto.setRemark(remark);
        memberAssetService.issue(dto);
    }

    private ShopOrderVO submitAndPay(DmsShopMember member) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO order = new ShopOrderSubmitDTO();
        order.setReceiverName("交付验收收货人");
        order.setReceiverPhone("18800000088");
        order.setReceiverProvince("湖南省");
        order.setReceiverCity("长沙市");
        order.setReceiverDistrict("岳麓区");
        order.setReceiverDetailAddress("验收路88号");
        order.setPayType("BALANCE");
        order.setItems(List.of(item));
        ShopOrderVO pending = shopService.submitOrder(order, member);
        assertEquals(0, pending.getOrder().getStatus());
        BalancePayDTO pay = new BalancePayDTO();
        pay.setPaymentPassword(PAYMENT_PASSWORD);
        ShopOrderVO paid = walletService.payOrder(member, pending.getOrder().getId(), pay);
        assertEquals(1, paid.getOrder().getStatus());
        return paid;
    }

    private void shipAndReceive(Long orderId, DmsShopMember member, String deliveryNo) {
        ShopOrderShipDTO ship = new ShopOrderShipDTO();
        ship.setDeliveryCompany("顺丰速运");
        ship.setDeliveryNo(deliveryNo);
        ship.setShipmentQuantity(1);
        assertTrue(shopService.shipOrder(orderId, ship));
        assertEquals(2, orderDao.selectById(orderId).getStatus());
        assertTrue(shopService.confirmReceive(orderId, member));
        assertEquals(3, orderDao.selectById(orderId).getStatus());
    }

    private void prepareDefaultReturnAddress() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_service_address
                  (tenant_id,address_type,address_label,contact_name,contact_phone,province,city,district,
                   detail_address,is_default,status)
                VALUES (1,2,'默认退货地址','验收售后','073112345678','湖南省','长沙市','岳麓区',
                        '退货路1号',1,1)
                """);
    }

    private ShopWithdrawalApplyDTO withdrawal(String paymentPassword, BigDecimal amount) {
        ShopWithdrawalApplyDTO dto = new ShopWithdrawalApplyDTO();
        dto.setWithdrawAmount(amount);
        dto.setWithdrawType(3);
        dto.setBankAccount("acceptance@example.com");
        dto.setAccountName("验收邀请人");
        dto.setPaymentPassword(paymentPassword);
        dto.setSmsCode("123456");
        return dto;
    }

    private BigDecimal balance(Long userId) {
        DmsMemberAssetAccount account = assetAccountDao.selectByUserIdAndAssetCode(userId, "CASH_BONUS");
        return account == null ? BigDecimal.ZERO : account.getBalance();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertMoney(new BigDecimal(expected), actual);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), "expected=" + expected + ", actual=" + actual);
    }

    @TestConfiguration
    static class CustomerPolicyFixtureConfig {
        @Bean
        TestCustomerBonusPolicy testCustomerBonusPolicy(DmsOrderRelationSnapshotDao relationSnapshotDao) {
            return new TestCustomerBonusPolicy(relationSnapshotDao);
        }
    }

    static class TestCustomerBonusPolicy implements CustomerBonusPolicy {
        static final String POLICY_CODE = "CUSTOMER_ACCEPTANCE_V1";
        static final String BONUS_CODE = "CUSTOMER_ACCEPTANCE_REWARD";

        private final DmsOrderRelationSnapshotDao relationSnapshotDao;
        private final AtomicInteger orderEvents = new AtomicInteger();
        private final AtomicInteger refundEvents = new AtomicInteger();

        TestCustomerBonusPolicy(DmsOrderRelationSnapshotDao relationSnapshotDao) {
            this.relationSnapshotDao = relationSnapshotDao;
        }

        @Override
        public String policyCode() {
            return POLICY_CODE;
        }

        @Override
        public List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context) {
            return relationSnapshotDao.selectByOrderId(context.orderId()).stream()
                    .filter(item -> Integer.valueOf(1).equals(item.getRelationLevel()))
                    .findFirst()
                    .map(item -> List.of(new CustomerBonusPayout(
                            item.getTargetAgentId(), 1, BONUS_CODE, new BigDecimal("0.0500"),
                            context.bonusBaseAmount().multiply(new BigDecimal("0.0500"))
                                    .setScale(2, RoundingMode.HALF_UP),
                            "交付验收客户独立制度")))
                    .orElseGet(List::of);
        }

        @Override
        public void afterOrder(CustomerBonusOrderContext context) {
            orderEvents.incrementAndGet();
        }

        @Override
        public void afterRefund(CustomerBonusRefundContext context) {
            refundEvents.incrementAndGet();
        }

        void reset() {
            orderEvents.set(0);
            refundEvents.set(0);
        }

        int orderEvents() {
            return orderEvents.get();
        }

        int refundEvents() {
            return refundEvents.get();
        }
    }
}
