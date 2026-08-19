package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.AssetTransferDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleItemDTO;
import com.macro.mall.distribution.dto.ShopManualRefundDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.enums.ChangeTypeEnum;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.impl.NewRetailBonusPolicy;
import com.macro.mall.distribution.service.impl.NewRetailRankService;
import com.macro.mall.distribution.vo.PersonProfileVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.CommissionRecordVO;
import com.macro.mall.distribution.vo.OrderAuditVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import lombok.extern.slf4j.Slf4j;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 业绩服务测试类
 * 使用H2内存数据库进行测试
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
    com.macro.mall.distribution.config.RedisConfig.class,
    com.macro.mall.distribution.config.ScheduleTask.class
}))
public class PerformanceServiceTest {

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    @Autowired
    private DmsAgentDao agentDao;

    @Autowired
    private DmsAgentRelationDao relationDao;

    @Autowired
    private DmsOrderPerformanceDetailDao performanceDetailDao;

    @Autowired
    private DmsCommissionRecordDao commissionRecordDao;

    @Autowired
    private DmsAgentAccountDao accountDao;

    @Autowired
    private DmsAgentChangeLogDao agentChangeLogDao;

    @Autowired
    private DmsCommissionRuleVersionDao commissionRuleVersionDao;

    @Autowired
    private DmsOrderRelationSnapshotDao relationSnapshotDao;

    @Autowired
    private DmsCommissionClawbackDao clawbackDao;

    @Autowired
    private DmsShopOrderDao shopOrderDao;

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private AgentAccountService agentAccountService;

    @Autowired
    private PerformanceService performanceService;

    @Autowired
    private DistributionAuditService auditService;

    @Autowired
    private MemberAssetService memberAssetService;

    @Autowired
    private BonusEngineConfigService bonusEngineConfigService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private CommissionSettlementService commissionSettlementService;

    @Autowired
    private OrderBalanceAllocationService orderBalanceAllocationService;

    @Autowired
    private DmsOrderBalanceAllocationDao orderBalanceAllocationDao;

    @Autowired
    private DmsOrderFinanceDao orderFinanceDao;

    @Autowired
    private DmsFinanceRefundDao financeRefundDao;

    @Autowired
    private DmsShopAfterSaleDao afterSaleDao;

    @Autowired
    private DmsOrderCompanyShareDao companyShareDao;

    @Autowired
    private NewRetailRankService newRetailRankService;

    @Autowired
    private ShopAuthService shopAuthService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopAfterSaleService shopAfterSaleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testAuditDefaultsToAllOrdersAndIncludesOrdinaryMemberIdentity() {
        DmsShopMember member = createShopMember("13999000040", "账务默认列表会员", null);
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO submit = new ShopOrderSubmitDTO();
        submit.setReceiverName(member.getNickname());
        submit.setReceiverPhone(member.getPhone());
        submit.setReceiverAddress("湖南省长沙市账务列表测试地址");
        submit.setPayType("ALIPAY");
        submit.setItems(List.of(item));
        ShopOrderVO created = shopService.submitOrder(submit, member);
        ShopOrderVO second = shopService.submitOrder(submit, member);

        String memberAccount = member.getUsername();
        OrderAuditVO defaultRow = auditService.getAllOrders().stream()
                .filter(row -> created.getOrder().getId().equals(row.getOrderId()))
                .findFirst()
                .orElseThrow();
        assertEquals(memberAccount, defaultRow.getOwnerMemberAccount());
        assertEquals(member.getNickname(), defaultRow.getOwnerMemberName());

        List<OrderAuditVO> memberOrders = auditService.getOrdersByMemberKey(memberAccount);
        assertTrue(memberOrders.stream().anyMatch(row -> created.getOrder().getId().equals(row.getOrderId())));
        assertTrue(memberOrders.stream().anyMatch(row -> second.getOrder().getId().equals(row.getOrderId())));
        assertTrue(auditService.getBonusSourcesByMemberKey(memberAccount).isEmpty(),
                "普通商城会员尚未产生奖金时应返回空奖金列表，而不是阻止查询订单");
        assertEquals(created.getOrder().getId(), auditService.getOrdersByOrderNo(created.getOrder().getOrderNo()).get(0).getOrderId());
        assertTrue(auditService.getOrdersByOrderNo("SO-NOT-FOUND").isEmpty());
        assertTrue(auditService.getBonusSourcesByOrderNo(created.getOrder().getOrderNo()).isEmpty());

        PageHelper.startPage(1, 1);
        PageInfo<OrderAuditVO> page = new PageInfo<>(auditService.getAllOrders());
        assertEquals(1, page.getList().size());
        assertTrue(page.getTotal() >= 2, "转换为账务列表后必须保留数据库分页总条数");
    }

    @Test
    void testPerformanceLookupAcceptsLoginAccountAndPhone() {
        AdminMemberCreateDTO create = new AdminMemberCreateDTO();
        create.setPhone("13999000041");
        create.setUsername("member_13999000041");
        create.setNickname("业绩查询会员");
        create.setActivateDistribution(true);
        create.setInitialLevel(1);
        create.setReason("验证业绩概览多种查询方式");
        DmsShopMember member = shopAuthService.createAdminMember(create);
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        assertNotNull(agent);

        assertEquals(agent.getId(), performanceService.resolveAgentId(member.getUsername()));
        assertEquals(agent.getId(), performanceService.resolveAgentId(member.getPhone()));

        String memberAccount = member.getUsername();
        assertEquals(memberAccount, agentService.getAgentById(agent.getId()).getMemberAccount());
        assertEquals(List.of(agent.getId()), agentService.listAgents(memberAccount, 1).stream()
                .map(AgentInfoVO::getId).toList());
    }

    @Test
    void testCommissionAdminQueryUsesLoginAccountAndReturnsLoginAccounts() {
        newRetailVersion("MEMBER_ACCOUNT_COMMISSION_QUERY");
        DmsShopMember inviter = createShopMember("13999000042", "编号查询直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember buyer = createShopMember("13999000043", "编号查询购买人", inviter.getUserId());
        ShopOrderVO paid = submitAndPay(buyer, 1);

        CommissionQueryDTO query = new CommissionQueryDTO();
        query.setMemberKey(inviter.getUsername());
        query.setOrderNo(paid.getOrder().getOrderNo());
        List<CommissionRecordVO> records = commissionService.getCommissionRecords(query);

        assertFalse(records.isEmpty());
        assertTrue(records.stream().allMatch(item ->
                inviter.getUsername().equals(item.getAgentMemberAccount())));
        assertTrue(records.stream().allMatch(item ->
                buyer.getUsername().equals(item.getOrderMemberAccount())));
    }

    /**
     * 测试场景1：基本业绩统计验证
     * A(一星董事)→B(VIP)→C(会员)
     * A做10000，B做5000，C做10000
     */
    @Test
    void testBasicPerformance() {
        log.info("=== 测试场景1：基本业绩统计 ===");

        // 验证代理数据存在
        DmsAgent agentA = agentDao.selectById(1L);
        assertNotNull(agentA, "代理A应该存在");
        assertEquals("张三(A)", agentA.getAgentName());
        log.info("代理A: {}", agentA.getAgentName());

        DmsAgent agentB = agentDao.selectById(2L);
        assertNotNull(agentB, "代理B应该存在");
        assertEquals("李四(B)", agentB.getAgentName());
        log.info("代理B: {}", agentB.getAgentName());

        DmsAgent agentC = agentDao.selectById(3L);
        assertNotNull(agentC, "代理C应该存在");
        assertEquals("王五(C)", agentC.getAgentName());
        log.info("代理C: {}", agentC.getAgentName());

        // 验证A的团队业绩（应该是25000：A自己10000 + B的5000 + C的10000）
        BigDecimal aTeamPerformance = getTeamPerformance(1L);
        log.info("A的团队业绩: {}", aTeamPerformance);
        assertEquals(new BigDecimal("25000.00"), aTeamPerformance, "A的团队业绩应该是25000");

        // 验证B的团队业绩（应该是15000：B自己5000 + C的10000）
        BigDecimal bTeamPerformance = getTeamPerformance(2L);
        log.info("B的团队业绩: {}", bTeamPerformance);
        assertEquals(new BigDecimal("15000.00"), bTeamPerformance, "B的团队业绩应该是15000");

        // 验证C的团队业绩（应该是10000：只有C自己）
        BigDecimal cTeamPerformance = getTeamPerformance(3L);
        log.info("C的团队业绩: {}", cTeamPerformance);
        assertEquals(new BigDecimal("10000.00"), cTeamPerformance, "C的团队业绩应该是10000");

        // 验证D的团队业绩（应该是10000：只有D自己）
        BigDecimal dTeamPerformance = getTeamPerformance(4L);
        log.info("D的团队业绩: {}", dTeamPerformance);
        assertEquals(new BigDecimal("10000.00"), dTeamPerformance, "D的团队业绩应该是10000");

        log.info("✅ 测试通过！");
    }

    @Test
    void testPerformanceOverviewAndRankingExposeTotalMonthAndNewAgents() {
        LocalDate today = java.time.LocalDate.now();
        var overview = performanceService.getPerformanceOverview(1L, today.minusDays(1), today);
        assertEquals(new BigDecimal("25000.00"), overview.getTeamPerformance());
        assertEquals(new BigDecimal("25000.00"), overview.getTotalTeamPerformance());
        assertEquals(new BigDecimal("25000.00"), overview.getCurrentMonthTeamPerformance());
        assertEquals(2, overview.getTotalNewAgentCount());
        assertEquals(2, overview.getCurrentMonthNewAgentCount());

        var ranking = performanceService.getPerformanceRanking(2, 3, today);
        var agentA = ranking.stream().filter(row -> row.getAgentId().equals(1L)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("25000.00"), agentA.getPerformanceValue());
        assertEquals(new BigDecimal("25000.00"), agentA.getTotalPerformance());
        assertEquals(new BigDecimal("25000.00"), agentA.getCurrentMonthPerformance());
        assertEquals(2, agentA.getCurrentMonthNewAgentCount());

        insertPerformanceDetail(19001L, "PROFILE-OLD-EFFECTIVE", 1L,
                new BigDecimal("500.00"), 1, today.minusMonths(1).atStartOfDay());
        insertPerformanceDetail(19002L, "PROFILE-INVALID", 1L,
                new BigDecimal("999.00"), 0, LocalDateTime.now());
        insertPerformanceDetail(19003L, "PROFILE-REFUND-ORIGINAL", 1L,
                new BigDecimal("100.00"), 1, LocalDateTime.now());
        insertPerformanceDetail(19003L, "PROFILE-REFUND-REVERSAL", 1L,
                new BigDecimal("-100.00"), 1, LocalDateTime.now());

        var profileSummary = performanceService.getProfilePerformanceSummary(1L, today);
        assertEquals(new BigDecimal("25500.00"), profileSummary.getTotalTeamPerformance());
        assertEquals(new BigDecimal("25000.00"), profileSummary.getCurrentMonthTeamPerformance());
    }

    @Test
    void testManualAssetAdjustmentExecutesImmediatelyAndCreatesFlow() {
        DmsAdminUser operator = admin(1L, "operator");
        AssetChangeDTO command = new AssetChangeDTO();
        command.setAgentId(2L);
        command.setAmount(new BigDecimal("25.00"));
        command.setBizType("MANUAL_MEMBER_ADJUST");
        command.setBizId("ORDER-TEST-1001");
        command.setRemark("人工补发余额，订单：ORDER-TEST-1001");
        try {
            AdminContext.set(operator);
            DmsMemberAssetFlow flow = memberAssetService.issue(command);
            assertNotNull(flow.getId());
            assertAmountEquals("0.00", flow.getBalanceBefore());
            assertAmountEquals("25.00", flow.getBalanceAfter());
            assertEquals(1L, flow.getOperatorId());
            assertEquals("operator", flow.getOperatorName());
            assertEquals(new BigDecimal("25.00"), memberAssetService.listAccounts(2L, null).stream()
                    .filter(item -> "CASH_BONUS".equals(item.getAssetCode())).findFirst().orElseThrow().getBalance());
            assertTrue(memberAssetService.listFlows(2L, null).stream()
                    .anyMatch(item -> item.getId().equals(flow.getId()) && "人工补发余额，订单：ORDER-TEST-1001".equals(item.getRemark())));
            var flowRecords = memberAssetService.searchBalanceFlows(
                    flow.getFlowNo(), null, "IN", "RECHARGE", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            assertEquals(1, flowRecords.size());
            assertEquals(flow.getId(), flowRecords.get(0).getId());
            assertAmountEquals("0.00", flowRecords.get(0).getBalanceBefore());
            assertEquals("operator", flowRecords.get(0).getOperatorName());
            var relatedRecords = memberAssetService.searchBalanceFlows(
                    null, "ORDER-TEST-1001", "IN", "RECHARGE", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            assertEquals(1, relatedRecords.size());
            assertEquals(flow.getId(), relatedRecords.get(0).getId());
            var summary = memberAssetService.summarizeBalanceFlows(
                    flow.getFlowNo(), null, "IN", "RECHARGE", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
            assertAmountEquals("25.00", summary.getTotalRechargeAmount());
            assertAmountEquals("25.00", summary.getTotalIncomeAmount());
            assertAmountEquals("0.00", summary.getTotalExpenseAmount());
        } finally {
            AdminContext.clear();
        }
    }

    @Test
    void testManualMonthlySettlementDisabledAndT7SettlementUsesReceiveTime() {
        assertThrows(RuntimeException.class, () -> commissionSettlementService.createBatch(null));

        newRetailVersion("T7_SETTLEMENT");
        DmsShopMember inviter = createShopMember("13999000031", "T7直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember buyer = createShopMember("13999000032", "T7购买人", inviter.getUserId());
        ShopOrderVO paid = submitAndPay(buyer, 1);
        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(paid.getOrder().getId());
        assertFalse(records.isEmpty());

        // 支付满7天但未确认收货仍不能结算。
        assertEquals(0, commissionSettlementService.settleEligibleAfterCoolingOff(100));
        jdbcTemplate.update("UPDATE dms_shop_order SET status=1, pay_time=?, receive_time=NULL WHERE id=?",
                LocalDateTime.now().minusDays(8), paid.getOrder().getId());
        assertEquals(0, commissionSettlementService.settleEligibleAfterCoolingOff(100));

        // 确认收货未满7天也不能结算。
        jdbcTemplate.update("UPDATE dms_shop_order SET status=3, receive_time=? WHERE id=?",
                LocalDateTime.now().minusDays(6), paid.getOrder().getId());
        assertEquals(0, commissionSettlementService.settleEligibleAfterCoolingOff(100));

        // 在签收后7天入口关闭前提交售后，之后即使已满7天，待处理售后仍会阻止结算。
        ShopAfterSaleItemDTO pendingItem = new ShopAfterSaleItemDTO();
        pendingItem.setOrderItemId(paid.getItems().get(0).getId());
        pendingItem.setQuantity(1);
        ShopAfterSaleApplyDTO pendingApply = new ShopAfterSaleApplyDTO();
        pendingApply.setOrderId(paid.getOrder().getId());
        pendingApply.setItems(List.of(pendingItem));
        pendingApply.setReason("验证待处理售后阻止T+7结算");
        shopAfterSaleService.apply(buyer, pendingApply);
        jdbcTemplate.update("UPDATE dms_shop_order SET receive_time=? WHERE id=?",
                LocalDateTime.now().minusDays(8), paid.getOrder().getId());
        assertEquals(0, commissionSettlementService.settleEligibleAfterCoolingOff(100));

        jdbcTemplate.update("UPDATE dms_shop_after_sale SET status=2 WHERE order_id=?",
                paid.getOrder().getId());
        assertEquals(records.size(), commissionSettlementService.settleEligibleAfterCoolingOff(100));
        assertTrue(commissionRecordDao.selectByOrderId(paid.getOrder().getId()).stream()
                .allMatch(item -> CommissionStatusEnum.SETTLED.getValue().equals(item.getStatus())));
    }

    @Test
    void testAdminCanCancelPendingRefundWithoutCreatingRefundLedger() {
        DmsShopMember buyer = createShopMember("13999000033", "取消售后测试", null);
        ShopOrderVO paid = submitAndPay(buyer, 1);
        ShopAfterSaleItemDTO item = new ShopAfterSaleItemDTO();
        item.setOrderItemId(paid.getItems().get(0).getId());
        item.setQuantity(1);
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(paid.getOrder().getId());
        apply.setItems(List.of(item));
        apply.setReason("验证后台取消退款");

        DmsShopAfterSale afterSale = shopAfterSaleService.apply(buyer, apply);
        ShopAfterSaleAuditDTO cancel = new ShopAfterSaleAuditDTO();
        cancel.setStatus(3);
        cancel.setAuditUserId(1L);
        cancel.setAuditUserName("test-admin");
        DmsShopAfterSale cancelled = shopAfterSaleService.audit(afterSale.getId(), cancel);

        assertEquals(3, cancelled.getStatus());
        assertTrue(auditService.getRefundsByOrderId(paid.getOrder().getId()).isEmpty(),
                "取消退款申请不能生成退款冲账记录");
    }

    @Test
    void customerCanCancelOwnPendingAfterSaleAndApplyAgain() {
        DmsShopMember buyer = createShopMember("13999000036", "客户撤回售后测试", null);
        ShopOrderVO paid = submitAndPay(buyer, 2);

        ShopAfterSaleItemDTO item = new ShopAfterSaleItemDTO();
        item.setOrderItemId(paid.getItems().get(0).getId());
        item.setQuantity(1);
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(paid.getOrder().getId());
        apply.setItems(List.of(item));
        apply.setReason("验证客户主动取消售后");

        DmsShopAfterSale first = shopAfterSaleService.apply(buyer, apply);
        DmsShopAfterSale cancelled = shopAfterSaleService.cancel(buyer, first.getId());

        assertEquals(3, cancelled.getStatus());
        assertEquals("客户主动取消售后申请", cancelled.getAuditRemark());
        assertTrue(auditService.getRefundsByOrderId(paid.getOrder().getId()).isEmpty(),
                "客户撤回待审核申请不能生成退款冲账记录");

        DmsShopAfterSale second = shopAfterSaleService.apply(buyer, apply);
        assertEquals(0, second.getStatus());
    }

    @Test
    void legacyOrderCreatedWindowClosesAfterSevenDaysButAdminCanRefundByQuantity() {
        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_mode='ORDER_CREATED', after_sale_window_days=7 WHERE id=1");
        sqlSessionTemplate.clearCache();
        DmsShopMember buyer = createShopMember("13999000035", "超期售后测试", null);
        ShopOrderVO paid = submitAndPay(buyer, 2);
        // 同一事务内 MyBatis 可能复用一级缓存；同步更新已加载对象，模拟已过期订单。
        paid.getOrder().setCreateTime(LocalDateTime.now().minusDays(8));
        jdbcTemplate.update("UPDATE dms_shop_order SET create_time=DATEADD('DAY', -8, CURRENT_TIMESTAMP) WHERE id=?",
                paid.getOrder().getId());
        sqlSessionTemplate.clearCache();

        ShopAfterSaleItemDTO frontItem = new ShopAfterSaleItemDTO();
        frontItem.setOrderItemId(paid.getItems().get(0).getId());
        frontItem.setQuantity(1);
        ShopAfterSaleApplyDTO frontApply = new ShopAfterSaleApplyDTO();
        frontApply.setOrderId(paid.getOrder().getId());
        frontApply.setItems(List.of(frontItem));
        assertThrows(RuntimeException.class, () -> shopAfterSaleService.apply(buyer, frontApply));

        ShopManualRefundDTO manual = new ShopManualRefundDTO();
        manual.setRefundMode("QUANTITY");
        manual.setItems(List.of(frontItem));
        manual.setReason("超期后台按盒数退款");
        manual.setOperatorId(1L);
        manual.setOperatorName("test-admin");
        DmsShopAfterSale refunded = shopAfterSaleService.manualRefund(paid.getOrder().getId(), manual);
        assertEquals(1, refunded.getStatus());
        assertEquals(1, refunded.getRefundQuantity());
        assertEquals(1, auditService.getRefundsByOrderId(paid.getOrder().getId()).size());
    }

    @Test
    void testOrderCostAndRemainderEnterRealBalanceAfterSevenDaysAndRefundCanCreateDebt() {
        // 系统内部账号分别接收剩余商品款和产品成本；两者不会作为客户会员展示。
        jdbcTemplate.update("INSERT INTO dms_shop_member "
                        + "(id,user_id,phone,login_account,password_hash,salt,nickname,invite_code,status) "
                        + "VALUES (1,1001,'13988000001','SYSTEM_REMAINDER','x','s','剩余商品款账户','CMP00001',1)");
        jdbcTemplate.update("INSERT INTO dms_shop_member "
                        + "(id,user_id,phone,login_account,password_hash,salt,nickname,invite_code,status) "
                        + "VALUES (5,1005,'13988000005','SYSTEM_PRODUCT_COST','x','s','产品成本账户','CMP00005',1)");

        DmsShopMember buyer = createShopMember("13999000050", "资金归集购买人", null);
        ShopOrderVO paid = submitAndPay(buyer, 2);
        com.macro.mall.distribution.dto.OrderFinanceDTO unsafeEdit = new com.macro.mall.distribution.dto.OrderFinanceDTO();
        unsafeEdit.setOrderId(paid.getOrder().getId());
        unsafeEdit.setProductCost(new BigDecimal("1.00"));
        assertThrows(RuntimeException.class, () -> auditService.upsertOrderFinance(unsafeEdit),
                "支付后的冻结成本不能被后台直接改写");
        assertThrows(RuntimeException.class, () -> auditService.saveCompanyShares(paid.getOrder().getId(), List.of()),
                "固定归集启用后不能再通过旧人工分账接口改写去向");
        List<DmsOrderBalanceAllocation> pending = orderBalanceAllocationService.listByOrderId(paid.getOrder().getId());
        assertEquals(2, pending.size());
        DmsOrderBalanceAllocation cost = pending.stream()
                .filter(row -> OrderBalanceAllocationService.PRODUCT_COST.equals(row.getAllocationType()))
                .findFirst().orElseThrow();
        DmsOrderBalanceAllocation remainder = pending.stream()
                .filter(row -> OrderBalanceAllocationService.REMAINDER.equals(row.getAllocationType()))
                .findFirst().orElseThrow();
        assertEquals("SYSTEM_PRODUCT_COST", cost.getTargetAccount());
        assertEquals("SYSTEM_REMAINDER", remainder.getTargetAccount());
        assertAmountEquals("236.00", cost.getCurrentAmount());
        assertAmountEquals("362.00", remainder.getCurrentAmount());

        // 未确认收货、确认收货不足7天都不能入账。
        assertEquals(0, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100));
        jdbcTemplate.update("UPDATE dms_shop_order SET status=3, receive_time=? WHERE id=?",
                LocalDateTime.now().minusDays(6), paid.getOrder().getId());
        sqlSessionTemplate.clearCache();
        assertEquals(0, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100));

        jdbcTemplate.update("UPDATE dms_shop_order SET receive_time=? WHERE id=?",
                LocalDateTime.now().minusDays(8), paid.getOrder().getId());
        sqlSessionTemplate.clearCache();
        assertEquals(2, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100));
        assertAmountEquals("362.00", memberAssetService.listAccounts(1L, null).get(0).getBalance());
        assertAmountEquals("236.00", memberAssetService.listAccounts(5L, null).get(0).getBalance());
        // 定时任务重复执行不能重复入账。
        assertEquals(0, orderBalanceAllocationService.settleEligibleAfterCoolingOff(100));

        AssetChangeDTO spendRemainder = new AssetChangeDTO();
        spendRemainder.setAgentId(1L);
        spendRemainder.setAmount(new BigDecimal("362.00"));
        spendRemainder.setBizType("TEST_SPEND_ALLOCATION");
        memberAssetService.consume(spendRemainder);
        AssetChangeDTO spendCost = new AssetChangeDTO();
        spendCost.setAgentId(5L);
        spendCost.setAmount(new BigDecimal("236.00"));
        spendCost.setBizType("TEST_SPEND_ALLOCATION");
        memberAssetService.consume(spendCost);

        // 已经花完后退1件：按该SKU冻结成本与商品净额各冲回一半，余额可变负表示欠款。
        ShopAfterSaleItemDTO refundItem = new ShopAfterSaleItemDTO();
        refundItem.setOrderItemId(paid.getItems().get(0).getId());
        refundItem.setQuantity(1);
        ShopManualRefundDTO manualRefund = new ShopManualRefundDTO();
        manualRefund.setRefundMode("QUANTITY");
        manualRefund.setItems(List.of(refundItem));
        manualRefund.setReason("签收窗口关闭后的后台资金归集部分退款");
        manualRefund.setOperatorId(1L);
        manualRefund.setOperatorName("test-admin");
        shopAfterSaleService.manualRefund(paid.getOrder().getId(), manualRefund);

        assertAmountEquals("-181.00", memberAssetService.listAccounts(1L, null).get(0).getBalance());
        assertAmountEquals("-118.00", memberAssetService.listAccounts(5L, null).get(0).getBalance());
        List<DmsOrderBalanceAllocation> afterRefund = orderBalanceAllocationDao.selectByOrderId(paid.getOrder().getId());
        assertAmountEquals("181.00", afterRefund.stream()
                .filter(row -> OrderBalanceAllocationService.REMAINDER.equals(row.getAllocationType()))
                .findFirst().orElseThrow().getReversedAmount());
        assertAmountEquals("118.00", afterRefund.stream()
                .filter(row -> OrderBalanceAllocationService.PRODUCT_COST.equals(row.getAllocationType()))
                .findFirst().orElseThrow().getReversedAmount());
    }

    @Test
    void testOrderFinanceFlagsPayoutRateRiskImmediatelyAfterCommissionGenerated() {
        newRetailVersion("PAYOUT_RISK_MONITOR");
        DmsFinanceRiskRule payoutRule = new DmsFinanceRiskRule();
        payoutRule.setRuleCode("BONUS_PAYOUT_RATE_MAX");
        payoutRule.setRuleName("奖金拨出率上限");
        payoutRule.setThresholdValue(new BigDecimal("0.20"));
        payoutRule.setEnabled(1);
        auditService.saveRiskRule(payoutRule);

        DmsShopMember inviter = createShopMember("13999000035", "拨出率监控直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember buyer = createShopMember("13999000036", "拨出率监控购买人", inviter.getUserId());
        ShopOrderVO paid = submitAndPay(buyer, 1);
        var finance = auditService.getOrderFinanceDetail(paid.getOrder().getId()).getFinance();
        assertEquals(1, finance.getRiskStatus());
        assertAmountEquals("74.75", finance.getBonusAmount());
        assertTrue(finance.getRemark().contains("奖金拨出率"));
    }

    @Test
    void testFinanceRiskAlertsCoverProfitRateLowerBound() {
        DmsFinanceRiskRule profitRule = new DmsFinanceRiskRule();
        profitRule.setRuleCode("PROFIT_RATE_MIN");
        profitRule.setRuleName("利润率下限");
        profitRule.setThresholdValue(new BigDecimal("1.00"));
        profitRule.setEnabled(1);
        auditService.saveRiskRule(profitRule);

        DmsShopMember member = createShopMember("13999000037", "利润率风控测试", null);
        ShopOrderVO paid = submitAndPay(member, 1);
        var alerts = auditService.getRiskAlerts("today", null, null);

        var alert = alerts.stream()
                .filter(item -> "PROFIT_RATE_MIN".equals(item.getRuleCode()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1.00").compareTo(alert.getThresholdValue()));
        assertTrue(alert.getCurrentValue().compareTo(alert.getThresholdValue()) < 0);
        assertTrue(paid.getOrder().getPayTime() != null);
    }

    @Test
    void testFinanceRiskAlertsCoverLossOrderCountUpperBound() {
        DmsFinanceRiskRule lossRule = new DmsFinanceRiskRule();
        lossRule.setRuleCode("LOSS_ORDER_COUNT_MAX");
        lossRule.setRuleName("亏损订单数上限");
        lossRule.setThresholdValue(BigDecimal.ZERO);
        lossRule.setEnabled(1);
        auditService.saveRiskRule(lossRule);

        DmsShopMember member = createShopMember("13999000038", "亏损订单风控测试", null);
        ShopOrderVO paid = submitAndPay(member, 1);
        jdbcTemplate.update("UPDATE dms_order_finance SET product_cost=?, company_profit=?, risk_status=1 WHERE order_id=?",
                paid.getOrder().getPayAmount().add(new BigDecimal("1.00")), new BigDecimal("-1.00"),
                paid.getOrder().getId());

        var alerts = auditService.getRiskAlerts("today", null, null);
        var alert = alerts.stream()
                .filter(item -> "LOSS_ORDER_COUNT_MAX".equals(item.getRuleCode()))
                .findFirst().orElseThrow();
        assertEquals(BigDecimal.ONE, alert.getCurrentValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(alert.getThresholdValue()));
    }

    @Test
    void testFinanceSummaryUsesOrderPayTimeInsteadOfFinanceCreateTime() {
        DmsShopMember member = createShopMember("13999000039", "支付时间账期测试", null);
        ShopOrderVO paid = submitAndPay(member, 1);
        jdbcTemplate.update("UPDATE dms_order_finance SET create_time=? WHERE order_id=?",
                LocalDateTime.now().minusDays(30), paid.getOrder().getId());

        var summary = auditService.getFinanceSummary("today", null, null);
        assertEquals(1L, summary.getOrderCount());
        assertTrue(summary.getPayAmount().compareTo(BigDecimal.ZERO) > 0);

        var daily = auditService.getFinanceDailySummary("today", null, null);
        assertEquals(1, daily.size());
        assertEquals(LocalDate.now(), daily.get(0).getStatDate());
    }

    private DmsAdminUser admin(Long id, String username) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername(username);
        return admin;
    }

    /**
     * 测试场景2：固定新零售奖金记录验证。
     * C下单10000元：B获30%直推奖，A获5%董事无限层团队分红；
     * B下单5000元：A获52%直推奖。
     */
    @Test
    void testCommissionCalculation() {
        log.info("=== 测试场景2：佣金计算 ===");

        // B获得C订单的VIP直推奖：10000 × 30%。
        BigDecimal bCommission = commissionRecordDao.selectUnsettledAmountByAgentId(2L);
        log.info("B的待结算佣金: {}", bCommission);
        assertEquals(new BigDecimal("3000.00"), bCommission, "B的待结算佣金应该是3000");

        // A获得C订单500元团队分红，以及B订单2600元直推奖。
        BigDecimal aCommission = commissionRecordDao.selectUnsettledAmountByAgentId(1L);
        log.info("A的待结算佣金: {}", aCommission);
        assertEquals(new BigDecimal("3100.00"), aCommission, "A的待结算佣金应该是3100");

        log.info("✅ 测试通过！");
    }

    /** 后台奖金列表必须由数据库按新版奖金类型、订单号和时间筛选。 */
    @Test
    void testCommissionRecordQueryUsesNewBonusTypeFilters() {
        CommissionQueryDTO query = new CommissionQueryDTO();
        query.setOrderNo("ORD20240630001");
        query.setBonusType("DIRECTOR_SHARE");
        query.setStartTime(LocalDateTime.now().minusYears(10));
        query.setEndTime(LocalDateTime.now().plusYears(10));

        List<CommissionRecordVO> records = commissionService.getCommissionRecords(query);

        assertEquals(1, records.size());
        CommissionRecordVO record = records.get(0);
        assertEquals("DIRECTOR_SHARE", record.getBonusType());
        assertEquals(5, record.getAgentLevel());
        assertEquals(2, record.getCommissionLevel());
        assertAmountEquals("0.05", record.getCommissionRate());
        assertAmountEquals("500.00", record.getCommissionAmount());
    }

    /**
     * 测试场景3：切线不修改历史业绩。
     * 组织关系变更只影响切线后产生的新订单，原始业绩明细永久保留。
     */
    @Test
    void testSwitchLinePerformance() {
        log.info("=== 测试场景3：切线后业绩归属 ===");

        // 切线前验证
        BigDecimal aBefore = getTeamPerformance(1L);
        BigDecimal dBefore = getTeamPerformance(4L);
        log.info("切线前 - A的团队业绩: {}, D的团队业绩: {}", aBefore, dBefore);

        // 执行切线：把B切到D下面
        // 1. 更新B的parent_id
        DmsAgent agentB = agentDao.selectById(2L);
        agentB.setParentId(4L);
        agentB.setAncestorIds("4");
        agentB.setLevelDepth(2);
        agentDao.update(agentB);

        // 2. 更新C的ancestor_ids
        DmsAgent agentC = agentDao.selectById(3L);
        agentC.setAncestorIds("4,2");
        agentC.setLevelDepth(3);
        agentDao.update(agentC);

        // 切线后验证
        BigDecimal aAfter = getTeamPerformance(1L);
        BigDecimal dAfter = getTeamPerformance(4L);
        BigDecimal bAfter = getTeamPerformance(2L);
        BigDecimal cAfter = getTeamPerformance(3L);

        log.info("切线后 - A的团队业绩: {}, D的团队业绩: {}, B的团队业绩: {}, C的团队业绩: {}",
                aAfter, dAfter, bAfter, cAfter);

        assertEquals(aBefore, aAfter, "旧上级的历史团队业绩必须保留");
        assertEquals(dBefore, dAfter, "新上级不能获得切线前的历史团队业绩");
        assertEquals(new BigDecimal("15000.00"), bAfter, "B的团队业绩应该是15000");
        assertEquals(new BigDecimal("10000.00"), cAfter, "C的团队业绩应该是10000");

        log.info("✅ 测试通过！");
    }

    @Test
    void testSwitchLineRebuildsWholeSubtreeWithoutMovingHistory() {
        BigDecimal oldParentHistory = getTeamPerformance(1L);
        BigDecimal newParentHistory = getTeamPerformance(4L);

        AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
        dto.setAgentId(2L);
        dto.setNewParentAgentId(4L);
        dto.setReason("测试整线迁移");
        assertTrue(agentService.switchLine(dto));

        DmsAgent moved = agentDao.selectById(2L);
        DmsAgent child = agentDao.selectById(3L);
        assertEquals(4L, moved.getParentId());
        assertEquals("4", moved.getAncestorIds());
        assertEquals("4,2", child.getAncestorIds());

        assertNotNull(relationDao.selectValidRelation(1002L, 1004L), "B应直属D");
        assertNotNull(relationDao.selectValidRelation(1003L, 1002L), "C应直属B");
        assertNotNull(relationDao.selectValidRelation(1003L, 1004L), "C应建立到D的间接关系");
        assertNull(relationDao.selectValidRelation(1002L, 1001L), "B到旧上级A的关系应失效");
        assertNull(relationDao.selectValidRelation(1003L, 1001L), "C到旧上级A的关系应失效");

        assertEquals(0, accountDao.selectByAgentId(1L).getTotalTeamMembers());
        assertEquals(2, accountDao.selectByAgentId(4L).getTotalTeamMembers());

        assertEquals(oldParentHistory, getTeamPerformance(1L), "旧上级历史业绩不得变化");
        assertEquals(newParentHistory, getTeamPerformance(4L), "新上级不得获得历史业绩");
    }

    /**
     * 测试场景5：代理账户验证
     */
    @Test
    void testAgentAccount() {
        log.info("=== 测试场景5：代理账户验证 ===");

        // 验证代理账户存在
        DmsAgentAccount accountA = accountDao.selectByAgentId(1L);
        assertNotNull(accountA, "A的账户应该存在");
        log.info("A的账户: 总佣金={}, 待结算={}, 已结算={}",
                accountA.getTotalCommission(),
                accountA.getUnsettledCommission(),
                accountA.getSettledCommission());

        DmsAgentAccount accountB = accountDao.selectByAgentId(2L);
        assertNotNull(accountB, "B的账户应该存在");
        log.info("B的账户: 总佣金={}, 待结算={}, 已结算={}",
                accountB.getTotalCommission(),
                accountB.getUnsettledCommission(),
                accountB.getSettledCommission());

        // 验证团队人数
        assertEquals(Integer.valueOf(2), accountA.getTotalTeamMembers(), "A的团队人数应该是2");
        assertEquals(Integer.valueOf(1), accountB.getTotalTeamMembers(), "B的团队人数应该是1");

        log.info("✅ 测试通过！");
    }

    /**
     * 测试场景6：历史退款欠款自动抵扣未来佣金
     */
    @Test
    void testCommissionDebtAutoOffset() {
        DmsCommissionClawback debt = new DmsCommissionClawback();
        debt.setRefundId(1L);
        debt.setCommissionRecordId(1L);
        debt.setOrderId(10001L);
        debt.setOrderNo("ORD20240630001");
        debt.setAgentId(2L);
        debt.setAgentUserId(1002L);
        debt.setAgentName("李四(B)");
        debt.setOriginalCommissionAmount(new BigDecimal("3000.00"));
        debt.setClawbackAmount(new BigDecimal("450.00"));
        debt.setDeductedAmount(BigDecimal.ZERO);
        debt.setDebtAmount(new BigDecimal("450.00"));
        debt.setClawbackType(3);
        debt.setStatus(2);
        debt.setReason("测试退款欠款");
        clawbackDao.insert(debt);

        insertSnapshot(20001L, "ORD20240701001", agentDao.selectById(3L), agentDao.selectById(2L), 1, 1L);
        insertSnapshot(20001L, "ORD20240701001", agentDao.selectById(3L), agentDao.selectById(1L), 2, 1L);
        commissionService.calculateAndRecordCommission(
                20001L, "ORD20240701001", new BigDecimal("1000.00"), 1003L, "王五(C)");
        List<DmsCommissionRecord> firstRecords = commissionRecordDao.selectByOrderId(20001L);
        DmsCommissionRecord firstBRecord = firstRecords.stream()
                .filter(record -> record.getAgentId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertAmountEquals("0.00", firstBRecord.getCommissionAmount());
        assertEquals(CommissionStatusEnum.REFUNDED.getValue(), firstBRecord.getStatus());
        assertAmountEquals("150.00", clawbackDao.sumDebtByAgentId(2L));

        insertSnapshot(20002L, "ORD20240701002", agentDao.selectById(3L), agentDao.selectById(2L), 1, 1L);
        insertSnapshot(20002L, "ORD20240701002", agentDao.selectById(3L), agentDao.selectById(1L), 2, 1L);
        commissionService.calculateAndRecordCommission(
                20002L, "ORD20240701002", new BigDecimal("1000.00"), 1003L, "王五(C)");
        List<DmsCommissionRecord> secondRecords = commissionRecordDao.selectByOrderId(20002L);
        DmsCommissionRecord secondBRecord = secondRecords.stream()
                .filter(record -> record.getAgentId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertAmountEquals("150.00", secondBRecord.getCommissionAmount());
        assertEquals(CommissionStatusEnum.PENDING.getValue(), secondBRecord.getStatus());
        assertAmountEquals("0.00", clawbackDao.sumDebtByAgentId(2L));

        DmsAgentAccount accountB = accountDao.selectByAgentId(2L);
        assertAmountEquals("150.00", accountB.getTotalCommission());
        assertAmountEquals("150.00", accountB.getUnsettledCommission());

        List<DmsCommissionClawback> firstOffsetFlows = clawbackDao.selectByOrderId(20001L);
        assertTrue(firstOffsetFlows.stream()
                .anyMatch(flow -> Integer.valueOf(4).equals(flow.getClawbackType())
                        && new BigDecimal("300.00").compareTo(flow.getClawbackAmount()) == 0));
        List<DmsCommissionClawback> secondOffsetFlows = clawbackDao.selectByOrderId(20002L);
        assertTrue(secondOffsetFlows.stream()
                .anyMatch(flow -> Integer.valueOf(4).equals(flow.getClawbackType())
                        && new BigDecimal("150.00").compareTo(flow.getClawbackAmount()) == 0));
    }

    /**
     * 测试场景7：人员全景查询聚合订单、奖金和未清欠款
     */
    @Test
    void testPersonProfile() {
        DmsCommissionClawback debt = new DmsCommissionClawback();
        debt.setRefundId(1L);
        debt.setCommissionRecordId(1L);
        debt.setOrderId(10001L);
        debt.setOrderNo("ORD20240630001");
        debt.setAgentId(2L);
        debt.setAgentUserId(1002L);
        debt.setAgentName("李四(B)");
        debt.setOriginalCommissionAmount(new BigDecimal("1500.00"));
        debt.setClawbackAmount(new BigDecimal("80.00"));
        debt.setDeductedAmount(BigDecimal.ZERO);
        debt.setDebtAmount(new BigDecimal("80.00"));
        debt.setClawbackType(3);
        debt.setStatus(2);
        debt.setReason("测试人员全景欠款");
        clawbackDao.insert(debt);

        PersonProfileVO profile = auditService.getPersonProfile(2L, null);
        assertNotNull(profile.getAgent());
        assertEquals(2L, profile.getAgent().getId());
        assertNotNull(profile.getAccount());
        assertFalse(profile.getOrders().isEmpty());
        assertFalse(profile.getCommissions().isEmpty());
        assertFalse(profile.getClawbacks().isEmpty());
        assertAmountEquals("80.00", profile.getPendingDebtAmount());
    }

    /**
     * 测试场景8：余额转账后仍进入收款方余额，并可用于商城消费
     */
    @Test
    void testMemberBalanceTransferAndConsume() {
        AssetChangeDTO issue = new AssetChangeDTO();
        issue.setAgentId(1L);
        issue.setAmount(new BigDecimal("100.00"));
        issue.setBizType("TEST");
        issue.setRemark("测试发放现金奖金");
        memberAssetService.issue(issue);

        AssetTransferDTO transfer = new AssetTransferDTO();
        transfer.setFromAgentId(1L);
        transfer.setToAgentId(2L);
        transfer.setAmount(new BigDecimal("40.00"));
        transfer.setBizType("TEST_TRANSFER");
        transfer.setRemark("测试转赠");
        memberAssetService.transfer(transfer);

        DmsMemberAssetAccount aCash = memberAssetService.listAccounts(1L, null).stream()
                .filter(account -> "CASH_BONUS".equals(account.getAssetCode()))
                .findFirst()
                .orElseThrow();
        assertAmountEquals("60.00", aCash.getBalance());

        DmsMemberAssetAccount bBalance = memberAssetService.listAccounts(2L, null).stream()
                .filter(account -> "CASH_BONUS".equals(account.getAssetCode()))
                .findFirst()
                .orElseThrow();
        assertAmountEquals("40.00", bBalance.getBalance());

        AssetChangeDTO consume = new AssetChangeDTO();
        consume.setAgentId(2L);
        consume.setAmount(new BigDecimal("10.00"));
        consume.setBizType("TEST_ORDER");
        consume.setRemark("测试余额消费");
        memberAssetService.consume(consume);

        DmsMemberAssetAccount bBalanceAfter = memberAssetService.listAccounts(2L, null).stream()
                .filter(account -> "CASH_BONUS".equals(account.getAssetCode()))
                .findFirst()
                .orElseThrow();
        assertAmountEquals("30.00", bBalanceAfter.getBalance());
        List<DmsMemberAssetFlow> bFlows = memberAssetService.listFlows(2L, null);
        assertTrue(bFlows.stream().anyMatch(flow -> "CASH_BONUS".equals(flow.getAssetCode())
                && Integer.valueOf(4).equals(flow.getChangeType())));
        assertTrue(bFlows.stream().anyMatch(flow -> "CASH_BONUS".equals(flow.getAssetCode())
                && Integer.valueOf(2).equals(flow.getChangeType())));
    }

    /** 测试场景9：当前正式模式的佣金结算固定100%进入余额。 */
    @Test
    void testCommissionSettleAlwaysCreditsBalance() {
        DmsCommissionRecord record = commissionRecordDao
                .selectByAgentIdAndStatus(2L, CommissionStatusEnum.PENDING.getValue())
                .get(0);
        assertAmountEquals("3000.00", record.getCommissionAmount());
        // 历史种子数据只有佣金明细，未同步待结算汇总；先补齐账务不变量再验证结算。
        accountDao.addUnsettledCommission(2L, record.getCommissionAmount());
        assertTrue(commissionService.settleCommission(record.getId()));

        List<DmsMemberAssetAccount> accounts = memberAssetService.listAccounts(2L, null);
        DmsMemberAssetAccount balanceAccount = accounts.stream()
                .filter(account -> "CASH_BONUS".equals(account.getAssetCode()))
                .findFirst()
                .orElseThrow();

        assertAmountEquals("3000.00", balanceAccount.getBalance());
    }

    @Test
    void unsettledCommissionCannotBecomeNegative() {
        DmsAgentAccount before = accountDao.selectByAgentId(2L);
        BigDecimal available = before.getUnsettledCommission();
        assertThrows(RuntimeException.class, () -> agentAccountService.subtractUnsettledCommission(
                2L, available.add(BigDecimal.ONE)));
        assertAmountEquals(available.toPlainString(), accountDao.selectByAgentId(2L).getUnsettledCommission());
    }

    @Test
    void financialAndAfterSaleQueriesAreTenantScopedByOwningOrder() {
        long orderId = 99000001L;
        jdbcTemplate.update("INSERT INTO dms_shop_order(id,order_no,tenant_id,user_id,receiver_name,receiver_phone,receiver_address,pay_amount,status,pay_time) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                orderId, "TENANT2-ORDER", 2L, 99001L, "租户二", "13900000000", "测试地址", new BigDecimal("100.00"), 3);
        jdbcTemplate.update("INSERT INTO dms_order_finance(order_id,order_no,pay_amount,net_pay_amount) VALUES(?,?,?,?)",
                orderId, "TENANT2-ORDER", new BigDecimal("100.00"), new BigDecimal("100.00"));
        jdbcTemplate.update("INSERT INTO dms_finance_refund(order_id,order_no,refund_no,refund_amount) VALUES(?,?,?,?)",
                orderId, "TENANT2-ORDER", "TENANT2-REFUND", new BigDecimal("10.00"));
        jdbcTemplate.update("INSERT INTO dms_shop_after_sale(after_sale_no,order_id,order_no,member_id,user_id,status) VALUES(?,?,?,?,?,?)",
                "TENANT2-AFTERSALE", orderId, "TENANT2-ORDER", 99001L, 99001L, 0);
        jdbcTemplate.update("INSERT INTO dms_order_company_share(order_id,order_no,account_id,account_name,share_rate,share_amount) VALUES(?,?,?,?,?,?)",
                orderId, "TENANT2-ORDER", 1L, "租户二公司", new BigDecimal("0.10"), new BigDecimal("10.00"));

        assertNull(orderFinanceDao.selectByOrderIdScoped(1L, orderId));
        assertTrue(financeRefundDao.selectByOrderIdScoped(1L, orderId).isEmpty());
        assertNull(afterSaleDao.selectByIdScoped(1L,
                jdbcTemplate.queryForObject("SELECT id FROM dms_shop_after_sale WHERE order_id=?", Long.class, orderId)));
        assertTrue(companyShareDao.selectByOrderIdScoped(1L, orderId).isEmpty());

        assertNotNull(orderFinanceDao.selectByOrderIdScoped(2L, orderId));
        assertEquals(1, financeRefundDao.selectByOrderIdScoped(2L, orderId).size());
        assertEquals(1, companyShareDao.selectByOrderIdScoped(2L, orderId).size());
        assertEquals(1L, orderFinanceDao.selectSummaryScoped(2L, null, null).getOrderCount());

        // 默认便捷方法也必须使用当前租户上下文。
        TenantContext.setTenantId(2L);
        try {
            assertNotNull(orderFinanceDao.selectByOrderId(orderId));
            assertEquals(1, financeRefundDao.selectByOrderId(orderId).size());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 新零售简版端到端验证：直属推荐人既是一星董事时，同时获得直推奖和无限层团队分红。
     * 方法级事务会在测试结束后回滚，绝不污染任何正式数据。
     */
    @Test
    void testNewRetailDirectBonusAndDirectorDividendUseBalance() {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(1L);
        version.setVersionNo("NEW_RETAIL_SIMPLE_DEFAULT");
        version.setVersionName("新零售自动化测试");
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now());
        commissionRuleVersionDao.insert(version);

        DmsAgent inviter = agentDao.selectById(2L);
        inviter.setAgentLevel(5); // 一星董事，直属直推奖 52%
        agentDao.update(inviter);
        DmsAgent buyer = agentDao.selectById(3L);
        buyer.setAgentLevel(5); // 下单人自己的订单不属于“团队分红”
        agentDao.update(buyer);

        long orderId = 909001L;
        String orderNo = "NEW-RETAIL-TEST-001";
        DmsOrderRelationSnapshot snapshot = new DmsOrderRelationSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setRuleVersionId(version.getId());
        snapshot.setOrderId(orderId);
        snapshot.setOrderNo(orderNo);
        snapshot.setOrderUserId(buyer.getUserId());
        snapshot.setOwnerAgentId(buyer.getId());
        snapshot.setTargetAgentId(inviter.getId());
        snapshot.setTargetUserId(inviter.getUserId());
        snapshot.setTargetAgentName(inviter.getAgentName());
        snapshot.setRelationLevel(1);
        snapshot.setRelationPath(inviter.getId() + "/" + buyer.getId());
        snapshot.setSnapshotTime(LocalDateTime.now());
        relationSnapshotDao.insert(snapshot);

        BigDecimal orderAmount = new BigDecimal("1000.00");
        performanceService.recordOrderPerformance(orderId, orderNo, orderAmount, 1, buyer.getUserId(), LocalDateTime.now());
        commissionService.calculateAndRecordCommission(1L, orderId, orderNo, orderAmount, buyer.getUserId(), buyer.getAgentName());

        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(orderId);
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(item -> item.getAgentId().equals(inviter.getId())
                && item.getCommissionRate().compareTo(new BigDecimal("0.52")) == 0
                && item.getCommissionAmount().compareTo(new BigDecimal("520.00")) == 0));
        assertTrue(records.stream().anyMatch(item -> item.getAgentId().equals(inviter.getId())
                && item.getCommissionRate().compareTo(new BigDecimal("0.05")) == 0
                && item.getCommissionAmount().compareTo(new BigDecimal("50.00")) == 0));

        records.forEach(item -> assertTrue(commissionService.settleCommission(item.getId())));
        assertAmountEquals("570.00", memberAssetService.listAccounts(inviter.getId(), null).stream()
                .filter(item -> "CASH_BONUS".equals(item.getAssetCode())).findFirst().orElseThrow().getBalance());
        assertFalse(memberAssetService.listAccounts(buyer.getId(), null).stream()
                .anyMatch(item -> "CASH_BONUS".equals(item.getAssetCode())
                        && item.getBalance().compareTo(BigDecimal.ZERO) > 0));
    }

    /** A→B→C 的订单直推奖只能给B，A虽然是团队上级但不是C的直推人。 */
    @Test
    void testNewRetailDirectBonusNeverPaysGrandparent() {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(1L);
        version.setVersionNo("NEW_RETAIL_SIMPLE_DEFAULT");
        version.setVersionName("直推边界测试");
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now());
        commissionRuleVersionDao.insert(version);

        DmsAgent a = agentDao.selectById(1L);
        DmsAgent b = agentDao.selectById(2L);
        DmsAgent c = agentDao.selectById(3L);
        long orderId = 909002L;
        DmsOrderRelationSnapshot directB = new DmsOrderRelationSnapshot();
        directB.setTenantId(1L); directB.setRuleVersionId(version.getId()); directB.setOrderId(orderId);
        directB.setOrderNo("DIRECT-BOUNDARY-001"); directB.setOrderUserId(c.getUserId()); directB.setOwnerAgentId(c.getId());
        directB.setTargetAgentId(b.getId()); directB.setTargetUserId(b.getUserId()); directB.setTargetAgentName(b.getAgentName());
        directB.setRelationLevel(1); directB.setRelationPath(b.getId() + "/" + c.getId()); directB.setSnapshotTime(LocalDateTime.now());
        relationSnapshotDao.insert(directB);
        DmsOrderRelationSnapshot teamA = new DmsOrderRelationSnapshot();
        BeanUtils.copyProperties(directB, teamA, "id");
        teamA.setTargetAgentId(a.getId()); teamA.setTargetUserId(a.getUserId()); teamA.setTargetAgentName(a.getAgentName());
        teamA.setRelationLevel(2); teamA.setRelationPath(a.getId() + "/" + b.getId() + "/" + c.getId());
        relationSnapshotDao.insert(teamA);

        commissionService.calculateAndRecordCommission(1L, orderId, "DIRECT-BOUNDARY-001",
                new BigDecimal("100.00"), c.getUserId(), c.getAgentName());

        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(orderId);
        assertTrue(records.stream().anyMatch(item -> b.getId().equals(item.getAgentId())
                && "DIRECT_REWARD".equals(item.getBonusType())));
        assertFalse(records.stream().anyMatch(item -> a.getId().equals(item.getAgentId())
                        && "DIRECT_REWARD".equals(item.getBonusType())),
                "A不是C的直推人，不能获得C订单的直推奖");
        assertTrue(records.stream().anyMatch(item -> a.getId().equals(item.getAgentId())
                        && "DIRECTOR_SHARE".equals(item.getBonusType())),
                "A是一星董事，可以获得自己的无限层团队分红；该奖金不是直推奖");
    }

    /** 一件商品计一单；本人和任意深度团队祖先都累计商品件数和对应业绩。 */
    @Test
    void testNewRetailProductUnitsAccumulateForSelfAndUnlimitedTeam() {
        List<DmsAgent> chain = new ArrayList<>();
        Long parentId = null;
        for (int index = 1; index <= 12; index++) {
            DmsAgent member = insertRankAgent("UNLIMITED_" + index, parentId, 1);
            chain.add(member);
            parentId = member.getId();
        }
        DmsAgent buyer = chain.get(chain.size() - 1);
        long orderId = 909010L;
        int relationLevel = 1;
        for (int index = chain.size() - 2; index >= 0; index--) {
            insertSnapshot(orderId, "UNLIMITED-10", buyer, chain.get(index), relationLevel++);
        }

        performanceService.recordOrderPerformance(orderId, "UNLIMITED-10", new BigDecimal("100.00"),
                10, buyer.getUserId(), LocalDateTime.now());
        newRetailRankService.refreshAllRanks();

        for (DmsAgent member : chain) {
            assertEquals(10, performanceDetailDao.sumEffectiveTeamUnits(member.getId()),
                    member.getAgentName() + "应累计10件商品");
            assertEquals(10, accountDao.selectByAgentId(member.getId()).getTotalOrders(),
                    member.getAgentName() + "账户累计件数应同步增加10件");
            assertEquals(2, agentDao.selectById(member.getId()).getAgentLevel(),
                    member.getAgentName() + "应由10件累计单量升级VIP");
        }
        assertEquals(new BigDecimal("100.00"), getTeamPerformance(chain.get(0).getId()));
        assertTrue(performanceDetailDao.selectByOrderId(orderId).stream()
                .anyMatch(item -> Integer.valueOf(11).equals(item.getRelationLevel())),
                "必须保存第11层团队业绩，证明累计和卡级计算没有层数上限");
    }

    /** 触发订单仍按支付前会员25%；升级完成后，下一笔及之后订单全部按VIP 30%。 */
    @Test
    void testNewRankTakesEffectFromOrderAfterTrigger() {
        DmsCommissionRuleVersion version = newRetailVersion("TRIGGER_RATE");
        DmsAgent inviter = insertRankAgent("TRIGGER_INVITER", null, 1);
        DmsAgent buyer = insertRankAgent("TRIGGER_BUYER", inviter.getId(), 1);
        insertEffectivePersonalOrders(inviter, 9, 910000L);
        long orderId = 909011L;
        insertSnapshot(orderId, "TRIGGER-RATE-001", buyer, inviter, 1, version.getId());

        performanceService.recordOrderPerformance(orderId, "TRIGGER-RATE-001", new BigDecimal("100.00"),
                1, buyer.getUserId(), LocalDateTime.now());
        commissionService.calculateAndRecordCommission(1L, orderId, "TRIGGER-RATE-001",
                new BigDecimal("100.00"), buyer.getUserId(), buyer.getAgentName());

        assertEquals(2, agentDao.selectById(inviter.getId()).getAgentLevel());
        DmsCommissionRecord direct = commissionRecordDao.selectByOrderId(orderId).stream()
                .filter(item -> inviter.getId().equals(item.getAgentId()))
                .findFirst().orElseThrow();
        assertAmountEquals("0.25", direct.getCommissionRate());
        assertAmountEquals("25.00", direct.getCommissionAmount());
        assertEquals(1, direct.getAgentLevel(), "奖金记录必须冻结触发单支付前卡级");

        DmsAgent nextBuyer = insertRankAgent("TRIGGER_NEXT_BUYER", inviter.getId(), 1);
        long nextOrderId = 909111L;
        insertSnapshot(nextOrderId, "TRIGGER-RATE-002", nextBuyer, inviter, 1, version.getId());
        performanceService.recordOrderPerformance(nextOrderId, "TRIGGER-RATE-002", new BigDecimal("100.00"),
                1, nextBuyer.getUserId(), LocalDateTime.now());
        commissionService.calculateAndRecordCommission(1L, nextOrderId, "TRIGGER-RATE-002",
                new BigDecimal("100.00"), nextBuyer.getUserId(), nextBuyer.getAgentName());
        DmsCommissionRecord nextDirect = commissionRecordDao.selectByOrderId(nextOrderId).stream()
                .filter(item -> "DIRECT_REWARD".equals(item.getBonusType()))
                .findFirst().orElseThrow();
        assertAmountEquals("0.30", nextDirect.getCommissionRate());
        assertAmountEquals("30.00", nextDirect.getCommissionAmount());
        assertEquals(2, nextDirect.getAgentLevel());
    }

    /** 不同董事等级可以分别获得一次无限层团队分红。 */
    @Test
    void testEveryDirectorAncestorGetsUnlimitedTeamDividend() {
        DmsCommissionRuleVersion version = newRetailVersion("DIRECTOR_CHAIN");
        DmsAgent twoStar = insertRankAgent("DIVIDEND_TWO_STAR", null, 6);
        DmsAgent oneStar = insertRankAgent("DIVIDEND_ONE_STAR", twoStar.getId(), 5);
        DmsAgent directVip = insertRankAgent("DIVIDEND_DIRECT_VIP", oneStar.getId(), 2);
        DmsAgent buyer = insertRankAgent("DIVIDEND_BUYER", directVip.getId(), 1);
        long orderId = 909012L;
        insertSnapshot(orderId, "DIVIDEND-CHAIN-001", buyer, directVip, 1, version.getId());
        for (int level = 2; level <= 6; level++) {
            DmsAgent middle = insertRankAgent("DIVIDEND_MIDDLE_" + level, null, 1);
            insertSnapshot(orderId, "DIVIDEND-CHAIN-001", buyer, middle, level, version.getId());
        }
        insertSnapshot(orderId, "DIVIDEND-CHAIN-001", buyer, oneStar, 7, version.getId());
        for (int level = 8; level <= 10; level++) {
            DmsAgent middle = insertRankAgent("DIVIDEND_MIDDLE_" + level, null, 1);
            insertSnapshot(orderId, "DIVIDEND-CHAIN-001", buyer, middle, level, version.getId());
        }
        insertSnapshot(orderId, "DIVIDEND-CHAIN-001", buyer, twoStar, 11, version.getId());

        performanceService.recordOrderPerformance(orderId, "DIVIDEND-CHAIN-001", new BigDecimal("100.00"),
                1, buyer.getUserId(), LocalDateTime.now());
        commissionService.calculateAndRecordCommission(1L, orderId, "DIVIDEND-CHAIN-001",
                new BigDecimal("100.00"), buyer.getUserId(), buyer.getAgentName());

        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(orderId);
        assertEquals(3, records.size());
        assertTrue(records.stream().anyMatch(item -> directVip.getId().equals(item.getAgentId())
                && item.getCommissionRate().compareTo(new BigDecimal("0.30")) == 0));
        assertTrue(records.stream().anyMatch(item -> oneStar.getId().equals(item.getAgentId())
                && item.getCommissionLevel() == 7
                && item.getCommissionRate().compareTo(new BigDecimal("0.05")) == 0));
        assertTrue(records.stream().anyMatch(item -> twoStar.getId().equals(item.getAgentId())
                && item.getCommissionLevel() == 11
                && item.getCommissionRate().compareTo(new BigDecimal("0.04")) == 0));
    }

    /** 同一董事等级无论链上有多少人，都只向距离订单最近的一人发放。 */
    @Test
    void testSameDirectorRankOnlyNearestAncestorGetsDividend() {
        DmsCommissionRuleVersion version = newRetailVersion("DIRECTOR_SAME_RANK_COMPRESS");
        DmsAgent farOneStar = insertRankAgent("DIVIDEND_FAR_ONE_STAR", null, 5);
        DmsAgent middleOneStar = insertRankAgent("DIVIDEND_MIDDLE_ONE_STAR", farOneStar.getId(), 5);
        DmsAgent nearOneStar = insertRankAgent("DIVIDEND_NEAR_ONE_STAR", middleOneStar.getId(), 5);
        DmsAgent buyer = insertRankAgent("DIVIDEND_COMPRESS_BUYER", nearOneStar.getId(), 1);
        long orderId = 909112L;

        // 故意打乱插入顺序，证明发放选择不依赖数据库行的插入顺序。
        insertSnapshot(orderId, "DIVIDEND-COMPRESS-001", buyer, farOneStar, 3, version.getId());
        insertSnapshot(orderId, "DIVIDEND-COMPRESS-001", buyer, nearOneStar, 1, version.getId());
        insertSnapshot(orderId, "DIVIDEND-COMPRESS-001", buyer, middleOneStar, 2, version.getId());

        commissionService.calculateAndRecordCommission(1L, orderId, "DIVIDEND-COMPRESS-001",
                new BigDecimal("100.00"), buyer.getUserId(), buyer.getAgentName());

        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(orderId);
        List<DmsCommissionRecord> shares = records.stream()
                .filter(item -> "DIRECTOR_SHARE".equals(item.getBonusType()))
                .toList();
        assertEquals(1, shares.size());
        assertEquals(nearOneStar.getId(), shares.get(0).getAgentId());
        assertEquals(1, shares.get(0).getCommissionLevel());
        assertAmountEquals("0.05", shares.get(0).getCommissionRate());

        // 直属邀请人本身是董事时，允许同时获得直推奖和该等级董事分红。
        assertTrue(records.stream().anyMatch(item -> nearOneStar.getId().equals(item.getAgentId())
                && "DIRECT_REWARD".equals(item.getBonusType())
                && item.getCommissionRate().compareTo(new BigDecimal("0.52")) == 0));
    }

    @Test
    void testMaximumConfiguredPayoutRateIsSeventyNinePercent() {
        assertAmountEquals("0.14", NewRetailBonusPolicy.maximumDirectorShareRate());
        BigDecimal maximum = NewRetailBonusPolicy.maximumTotalPayoutRate();
        assertAmountEquals("0.79", maximum);
    }

    /** 全额退款把10件冲销为0，并允许VIP自动降回会员；退款日志必须保留。 */
    @Test
    void testFullRefundReversesUnitsAndDowngradesRank() {
        newRetailVersion("REFUND_DOWNGRADE");
        DmsAgent member = insertRankAgent("REFUND_MEMBER", null, 1);
        long orderId = 909013L;
        performanceService.recordOrderPerformance(orderId, "REFUND-DOWN-001", new BigDecimal("100.00"),
                10, member.getUserId(), LocalDateTime.now());
        newRetailRankService.refreshAllRanks();
        assertEquals(2, agentDao.selectById(member.getId()).getAgentLevel());

        performanceService.reverseOrderPerformance(orderId, 909013L, new BigDecimal("100.00"), 10, LocalDateTime.now());
        newRetailRankService.refreshAllRanksAfterRefund(orderId, 909013L);

        assertEquals(0, performanceDetailDao.sumEffectiveTeamUnits(member.getId()));
        assertEquals(0, accountDao.selectByAgentId(member.getId()).getTotalOrders());
        assertEquals(1, agentDao.selectById(member.getId()).getAgentLevel());
        assertTrue(agentChangeLogDao.selectByAgentIdAndChangeType(member.getId(), ChangeTypeEnum.DOWNGRADE.getValue())
                .stream().anyMatch(item -> item.getChangeDetail().contains("refundId")));
    }

    /** 真实商城链路：后台建账号→正常提交10件订单→支付→自动VIP→售后全退→自动降回会员。 */
    @Test
    void testRealShopTenUnitOrderAndAfterSaleRefundFlow() {
        newRetailVersion("REAL_SHOP_FLOW");
        AdminMemberCreateDTO create = new AdminMemberCreateDTO();
        create.setPhone("13999000010");
        create.setUsername("member_13999000010");
        create.setNickname("十件实单会员");
        create.setActivateDistribution(false);
        DmsShopMember member = shopAuthService.createAdminMember(create);

        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(10);
        ShopOrderSubmitDTO submit = new ShopOrderSubmitDTO();
        submit.setReceiverName("测试收货人");
        submit.setReceiverPhone("13999000010");
        submit.setReceiverAddress("湖南省长沙市测试路10号");
        submit.setPayType("ALIPAY");
        submit.setItems(List.of(item));

        ShopOrderVO created = shopService.submitOrder(submit, member);
        assertEquals(0, created.getOrder().getStatus());
        int unrelatedLevelBeforeRefund = agentDao.selectById(4L).getAgentLevel();
        ShopOrderVO paid = shopService.markOrderPaid(created.getOrder().getId(), "ALIPAY");
        assertEquals(1, paid.getOrder().getStatus());

        DmsAgent activated = agentDao.selectByUserId(member.getUserId());
        assertNotNull(activated);
        assertEquals(2, activated.getAgentLevel());
        assertEquals(10, performanceDetailDao.sumEffectiveTeamUnits(activated.getId()));
        assertEquals(10, accountDao.selectByAgentId(activated.getId()).getTotalOrders());

        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(created.getOrder().getId());
        ShopAfterSaleItemDTO refundItem = new ShopAfterSaleItemDTO();
        refundItem.setOrderItemId(paid.getItems().get(0).getId());
        refundItem.setQuantity(10);
        apply.setItems(List.of(refundItem));
        apply.setReason("完整实单退款回归");
        DmsShopAfterSale afterSale = shopAfterSaleService.apply(member, apply);
        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditUserId(1L);
        audit.setAuditUserName("test-admin");
        shopAfterSaleService.audit(afterSale.getId(), audit);

        assertEquals(afterSale.getAfterSaleNo(),
                auditService.getRefundsByOrderId(created.getOrder().getId()).get(0).getRefundNo());

        assertEquals(4, shopOrderDao.selectById(created.getOrder().getId()).getStatus());
        assertEquals(0, performanceDetailDao.sumEffectiveTeamUnits(activated.getId()));
        // 全额退款且名下无其他有效支付订单：按新规则自动取消会员资格（调整为非会员），
        // 推广身份记录移除；余额钱包和历史流水保留。
        assertNull(agentDao.selectByUserId(member.getUserId()));
        assertNull(agentDao.selectById(activated.getId()));
        assertNull(accountDao.selectByAgentId(activated.getId()));
        assertEquals(unrelatedLevelBeforeRefund, agentDao.selectById(4L).getAgentLevel(),
                "退款只能重算订单本人及其支付快照上级，不能降级无关会员");
    }

    /** 已发货后部分退货必须按实际件数冲减，原发货运费锁定不退。 */
    @Test
    void testPartialAfterShipmentRefundReversesExactQuantityAndNeverRefundsFreight() {
        newRetailVersion("PARTIAL_REFUND_EXACT_QUANTITY");
        jdbcTemplate.update("UPDATE dms_shop_product SET freight_type=1, freight_amount=12.00 WHERE id=1");
        DmsShopMember inviter = createShopMember("13999000033", "部分退款直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember member = createShopMember("13999000034", "部分退货会员", inviter.getUserId());
        ShopOrderVO paid = submitAndPay(member, 10);
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        assertEquals(10, accountDao.selectByAgentId(agent.getId()).getTotalOrders());
        jdbcTemplate.update("UPDATE dms_shop_order SET status=2, delivery_time=CURRENT_TIMESTAMP WHERE id=?", paid.getOrder().getId());

        ShopAfterSaleItemDTO refundItem = new ShopAfterSaleItemDTO();
        refundItem.setOrderItemId(paid.getItems().get(0).getId());
        refundItem.setQuantity(3);
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(paid.getOrder().getId());
        apply.setApplyType(2);
        apply.setItems(List.of(refundItem));
        apply.setReason("已发货部分退3件");
        DmsShopAfterSale afterSale = shopAfterSaleService.apply(member, apply);
        assertEquals(3, afterSale.getRefundQuantity());
        assertAmountEquals("0.00", afterSale.getFreightRefundAmount());

        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditUserId(1L);
        audit.setAuditUserName("test-admin");
        shopAfterSaleService.audit(afterSale.getId(), audit);

        assertEquals(7, performanceDetailDao.sumEffectiveTeamUnits(agent.getId()));
        assertEquals(7, accountDao.selectByAgentId(agent.getId()).getTotalOrders());
        assertEquals(3, auditService.getRefundsByOrderId(paid.getOrder().getId()).get(0).getRefundQuantity());
        assertAmountEquals("0.00", auditService.getRefundsByOrderId(paid.getOrder().getId()).get(0).getFreightRefundAmount());
        DmsCommissionRecord direct = commissionRecordDao.selectByOrderId(paid.getOrder().getId()).stream()
                .filter(item -> "DIRECT_REWARD".equals(item.getBonusType())).findFirst().orElseThrow();
        assertAmountEquals("523.25", direct.getCommissionAmount());

        // 再退3件：累计退60%，本次只补追30%，不能把累计60%再重复追一次。
        ShopAfterSaleApplyDTO secondApply = new ShopAfterSaleApplyDTO();
        secondApply.setOrderId(paid.getOrder().getId());
        secondApply.setApplyType(2);
        ShopAfterSaleItemDTO secondItem = new ShopAfterSaleItemDTO();
        secondItem.setOrderItemId(paid.getItems().get(0).getId());
        secondItem.setQuantity(3);
        secondApply.setItems(List.of(secondItem));
        secondApply.setReason("再次部分退3件");
        DmsShopAfterSale second = shopAfterSaleService.apply(member, secondApply);
        shopAfterSaleService.audit(second.getId(), audit);
        assertEquals(4, performanceDetailDao.sumEffectiveTeamUnits(agent.getId()));
        assertAmountEquals("299.00", commissionRecordDao.selectById(direct.getId()).getCommissionAmount());
    }

    /** 已结算奖金已被消费时，退款仍须成功，未追回部分形成欠款并按净奖金重算财务。 */
    @Test
    void testSettledCommissionSpentBeforeRefundCreatesDebtInsteadOfBlockingRefund() {
        newRetailVersion("SETTLED_SPENT_REFUND_DEBT");
        DmsShopMember inviter = createShopMember("13999000041", "已消费奖金直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember buyer = createShopMember("13999000042", "退款购买人", inviter.getUserId());
        ShopOrderVO paid = submitAndPay(buyer, 1);

        DmsCommissionRecord direct = commissionRecordDao.selectByOrderId(paid.getOrder().getId()).stream()
                .filter(item -> "DIRECT_REWARD".equals(item.getBonusType()))
                .findFirst().orElseThrow();
        assertTrue(commissionService.settleCommission(direct.getId()));

        AssetChangeDTO spend = new AssetChangeDTO();
        spend.setAgentId(direct.getAgentId());
        spend.setAmount(direct.getCommissionAmount());
        spend.setBizType("TEST_SPEND_SETTLED_COMMISSION");
        spend.setBizId(String.valueOf(direct.getId()));
        spend.setRemark("模拟已结算奖金被会员消费");
        memberAssetService.consume(spend);

        ShopAfterSaleItemDTO refundItem = new ShopAfterSaleItemDTO();
        refundItem.setOrderItemId(paid.getItems().get(0).getId());
        refundItem.setQuantity(1);
        ShopAfterSaleApplyDTO apply = new ShopAfterSaleApplyDTO();
        apply.setOrderId(paid.getOrder().getId());
        apply.setItems(List.of(refundItem));
        apply.setReason("全额退款验证欠款");
        DmsShopAfterSale afterSale = shopAfterSaleService.apply(buyer, apply);

        ShopAfterSaleAuditDTO audit = new ShopAfterSaleAuditDTO();
        audit.setStatus(1);
        audit.setAuditUserId(1L);
        audit.setAuditUserName("test-admin");
        assertDoesNotThrow(() -> shopAfterSaleService.audit(afterSale.getId(), audit));

        assertAmountEquals(direct.getCommissionAmount().toPlainString(),
                clawbackDao.sumDebtByAgentId(direct.getAgentId()));
        assertAmountEquals("0.00", auditService.getOrderFinanceDetail(paid.getOrder().getId())
                .getFinance().getBonusAmount());
    }

    /** 真实三笔订单：触发单按会员25%，升级完成后的下一单按VIP 30%。 */
    @Test
    void testRealShopNewRankTakesEffectAfterTriggerOrder() {
        newRetailVersion("REAL_TRIGGER_RATE");
        DmsShopMember inviterMember = createShopMember("13999000021", "真实直推人", null);
        ShopOrderVO inviterOrder = submitAndPay(inviterMember, 9);
        DmsAgent inviter = agentDao.selectByUserId(inviterMember.getUserId());
        assertNotNull(inviter);
        assertEquals(1, inviter.getAgentLevel());
        assertEquals(9, accountDao.selectByAgentId(inviter.getId()).getTotalOrders());

        DmsShopMember buyerMember = createShopMember("13999000022", "真实直推下级", inviterMember.getUserId());
        ShopOrderVO triggerOrder = submitAndPay(buyerMember, 1);

        assertEquals(2, agentDao.selectById(inviter.getId()).getAgentLevel());
        assertEquals(10, accountDao.selectByAgentId(inviter.getId()).getTotalOrders());
        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(triggerOrder.getOrder().getId());
        assertEquals(1, records.size());
        assertEquals(inviter.getId(), records.get(0).getAgentId());
        assertAmountEquals("0.25", records.get(0).getCommissionRate());
        assertAmountEquals("74.75", records.get(0).getCommissionAmount());

        DmsShopMember nextBuyerMember = createShopMember("13999000023", "真实后续直推下级", inviterMember.getUserId());
        ShopOrderVO nextOrder = submitAndPay(nextBuyerMember, 1);
        DmsCommissionRecord nextDirect = commissionRecordDao.selectByOrderId(nextOrder.getOrder().getId()).stream()
                .filter(item -> "DIRECT_REWARD".equals(item.getBonusType()))
                .findFirst().orElseThrow();
        assertAmountEquals("0.30", nextDirect.getCommissionRate());
        assertAmountEquals("89.70", nextDirect.getCommissionAmount());
        assertTrue(commissionRecordDao.selectByOrderId(inviterOrder.getOrder().getId()).isEmpty(),
                "本人订单不产生自己的直推奖或团队分红");
    }

    /**
     * 覆盖一星董事至合伙人的双部门晋级判断。订单计数使用真实业绩明细，
     * 下级卡级别作为已达标部门的输入，避免在单测中构建数千笔递归订单。
     */
    @Test
    void testNewRetailAutoPromotionFromStarDirectorToPartner() {
        for (int expectedLevel = 5; expectedLevel <= 8; expectedLevel++) {
            DmsAgent candidate = insertRankAgent("RANK_AUTO_" + expectedLevel, null, 1);
            for (int childIndex = 1; childIndex <= 5; childIndex++) {
                int childLevel = childIndex <= 2 ? expectedLevel - 1 : 2;
                insertRankAgent("RANK_AUTO_" + expectedLevel + "_D" + childIndex, candidate.getId(), childLevel);
            }
            insertEffectivePersonalOrders(candidate, 500, expectedLevel * 100000L);
        }

        newRetailRankService.refreshAllRanks();

        for (int expectedLevel = 5; expectedLevel <= 8; expectedLevel++) {
            String candidateName = "RANK_AUTO_" + expectedLevel;
            DmsAgent candidate = agentDao.search(candidateName, null).stream()
                    .filter(item -> candidateName.equals(item.getAgentName()))
                    .findFirst().orElseThrow();
            assertEquals(expectedLevel, candidate.getAgentLevel(), "应自动晋级到等级 " + expectedLevel);
        }
    }

    /** A邀请B、B邀请C时，C不能算作A的直推VIP。 */
    @Test
    void testNewRetailDirectReferralCountsOnlyFirstGeneration() {
        DmsAgent a = insertRankAgent("DIRECT_ONLY_A", null, 1);
        DmsAgent b1 = insertRankAgent("DIRECT_ONLY_B1", a.getId(), 2);
        insertRankAgent("DIRECT_ONLY_B2", a.getId(), 2);
        insertRankAgent("DIRECT_ONLY_B3", a.getId(), 1);
        insertRankAgent("DIRECT_ONLY_B4", a.getId(), 1);
        insertRankAgent("DIRECT_ONLY_B5", a.getId(), 1);
        insertRankAgent("INDIRECT_C", b1.getId(), 2);
        insertEffectivePersonalOrders(a, 150, 880000L);

        newRetailRankService.refreshAllRanks();

        DmsAgent refreshed = agentDao.selectById(a.getId());
        assertEquals(3, refreshed.getAgentLevel(),
                "A只有2个直属VIP；B直属邀请的C不能凑成A的第3个直属VIP，因此A只能到店铺，不能到代理");
        List<DmsAgentChangeLog> logs = agentChangeLogDao.selectByAgentIdAndChangeType(
                a.getId(), ChangeTypeEnum.UPGRADE.getValue());
        assertTrue(logs.stream().anyMatch(item -> Integer.valueOf(3).equals(item.getNewLevel())
                && item.getChangeReason().contains("自动调级")));
    }

    /**
     * 获取代理的团队业绩
     */
    private BigDecimal getTeamPerformance(Long agentId) {
        List<DmsOrderPerformanceDetail> details = performanceDetailDao.selectByTargetAgentId(agentId);
        return details.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void insertPerformanceDetail(Long orderId, String orderNo, Long targetAgentId,
                                         BigDecimal amount, int status, LocalDateTime orderTime) {
        jdbcTemplate.update("""
                INSERT INTO dms_order_performance_detail
                    (order_id, order_no, order_amount, order_time, owner_user_id, owner_agent_id,
                     owner_agent_name, target_agent_id, target_agent_name, relation_level, quantity,
                     product_amount, performance_type, performance_amount, status)
                VALUES (?, ?, ?, ?, 1001, 1, '业绩测试会员', ?, '业绩测试会员', 0, 1, ?, 1, ?, ?)
                """, orderId, orderNo, amount.abs(), orderTime, targetAgentId, amount, amount, status);
    }

    private DmsShopMember createShopMember(String phone, String nickname, Long inviterUserId) {
        AdminMemberCreateDTO create = new AdminMemberCreateDTO();
        create.setPhone(phone);
        create.setUsername("member_" + phone);
        create.setNickname(nickname);
        create.setInviterUserId(inviterUserId);
        create.setActivateDistribution(false);
        return shopAuthService.createAdminMember(create);
    }

    private ShopOrderVO submitAndPay(DmsShopMember member, int quantity) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(quantity);
        ShopOrderSubmitDTO submit = new ShopOrderSubmitDTO();
        submit.setReceiverName(member.getNickname());
        submit.setReceiverPhone(member.getPhone());
        submit.setReceiverAddress("湖南省长沙市真实订单测试地址");
        submit.setPayType("ALIPAY");
        submit.setItems(List.of(item));
        ShopOrderVO order = shopService.submitOrder(submit, member);
        return shopService.markOrderPaid(order.getOrder().getId(), "ALIPAY");
    }

    private DmsAgent insertRankAgent(String name, Long parentId, int level) {
        DmsAgent agent = new DmsAgent();
        long marker = Math.abs(name.hashCode());
        agent.setUserId(8_000_000L + marker);
        agent.setAgentCode("TEST_" + name);
        agent.setAgentName(name);
        agent.setAgentLevel(level);
        agent.setParentId(parentId);
        agent.setLevelDepth(parentId == null ? 1 : 2);
        agent.setInviteCode("I" + marker);
        agent.setPhone("15" + String.format("%09d", marker % 1_000_000_000L));
        agent.setStatus(1);
        agent.setSourceType(1);
        agentDao.insert(agent);

        DmsAgentAccount account = new DmsAgentAccount();
        account.setAgentId(agent.getId());
        account.setUserId(agent.getUserId());
        account.setTotalCommission(BigDecimal.ZERO);
        account.setSettledCommission(BigDecimal.ZERO);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setFrozenCommission(BigDecimal.ZERO);
        account.setWithdrawnAmount(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setTotalOrders(0);
        account.setTotalTeamMembers(0);
        accountDao.insert(account);
        return agent;
    }

    private void insertEffectivePersonalOrders(DmsAgent agent, int count, long orderIdStart) {
        List<DmsOrderPerformanceDetail> details = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            DmsOrderPerformanceDetail detail = new DmsOrderPerformanceDetail();
            detail.setOrderId(orderIdStart + index);
            detail.setOrderNo("RANK-" + orderIdStart + "-" + index);
            detail.setOrderAmount(BigDecimal.ONE);
            detail.setOrderTime(LocalDateTime.now());
            detail.setOwnerUserId(agent.getUserId());
            detail.setOwnerAgentId(agent.getId());
            detail.setOwnerAgentName(agent.getAgentName());
            detail.setTargetAgentId(agent.getId());
            detail.setTargetAgentName(agent.getAgentName());
            detail.setRelationLevel(0);
            detail.setQuantity(1);
            detail.setProductAmount(BigDecimal.ONE);
            detail.setPerformanceType(1);
            detail.setPerformanceAmount(BigDecimal.ONE);
            detail.setStatus(1);
            details.add(detail);
        }
        performanceDetailDao.insertBatch(details);
    }

    private DmsCommissionRuleVersion newRetailVersion(String suffix) {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(1L);
        version.setVersionNo("NEW_RETAIL_SIMPLE_DEFAULT");
        version.setVersionName("新零售测试-" + suffix);
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now().plusSeconds(1));
        commissionRuleVersionDao.insert(version);
        return version;
    }

    private void insertSnapshot(long orderId, String orderNo, DmsAgent owner, DmsAgent target, int level) {
        insertSnapshot(orderId, orderNo, owner, target, level, null);
    }

    private void insertSnapshot(long orderId, String orderNo, DmsAgent owner, DmsAgent target,
                                int level, Long ruleVersionId) {
        DmsOrderRelationSnapshot snapshot = new DmsOrderRelationSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setRuleVersionId(ruleVersionId);
        snapshot.setOrderId(orderId);
        snapshot.setOrderNo(orderNo);
        snapshot.setOrderUserId(owner.getUserId());
        snapshot.setOwnerAgentId(owner.getId());
        snapshot.setTargetAgentId(target.getId());
        snapshot.setTargetUserId(target.getUserId());
        snapshot.setTargetAgentName(target.getAgentName());
        snapshot.setRelationLevel(level);
        snapshot.setRelationPath(target.getId() + "/" + owner.getId());
        snapshot.setSnapshotTime(LocalDateTime.now());
        relationSnapshotDao.insert(snapshot);
    }
}
