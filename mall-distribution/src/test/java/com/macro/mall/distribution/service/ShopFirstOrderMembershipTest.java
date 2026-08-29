package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.impl.ShopServiceImpl;
import com.macro.mall.distribution.service.impl.ShopAfterSaleWindowPolicy;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.OrderFinanceDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopFirstOrderMembershipTest {
    @Mock private DmsShopProductDao productDao;
    @Mock private DmsShopCategoryDao categoryDao;
    @Mock private DmsShopBannerDao bannerDao;
    @Mock private DmsShopNoticeDao noticeDao;
    @Mock private DmsShopSkuDao skuDao;
    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderItemDao orderItemDao;
    @Mock private DmsShopOrderShipmentDao orderShipmentDao;
    @Mock private DmsShopAddressDao addressDao;
    @Mock private DmsShopAfterSaleDao afterSaleDao;
    @Mock private DmsOrderPvDetailDao orderPvDetailDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsAgentAccountDao accountDao;
    @Mock private DmsTenantDao tenantDao;
    @Mock private DmsTenantDisplayConfigDao displayConfigDao;
    @Mock private DmsMigrationBaselineDao migrationBaselineDao;
    @Mock private DistributionAuditService auditService;
    @Mock private PerformanceService performanceService;
    @Mock private CommissionService commissionService;
    @Mock private MemberAssetService memberAssetService;
    @Mock private OrderBalanceAllocationService orderBalanceAllocationService;
    @Mock private OrderRelationSnapshotService relationSnapshotService;
    @Mock private ShopAuthService authService;
    @Mock private ErpIntegrationService erpIntegrationService;
    @Mock private MerchantService merchantService;
    @Mock private ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    @InjectMocks private ShopServiceImpl shopService;

    @Test
    void firstPaidNormalOrderActivatesLevelOneMembership() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(90001L);
        order.setOrderNo("FIRST-ORDER-90001");
        order.setTenantId(1L);
        order.setUserId(80001L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("299.00"));

        AgentInfoVO activated = new AgentInfoVO();
        activated.setId(70001L);
        activated.setUserId(80001L);
        activated.setAgentLevel(1);
        DmsAgent agent = new DmsAgent();
        agent.setId(70001L);
        agent.setUserId(80001L);
        agent.setAgentName("首单会员");
        agent.setStatus(1);

        when(orderDao.selectByIdForUpdate(90001L)).thenReturn(order);
        when(orderDao.selectById(90001L)).thenReturn(order);
        when(orderDao.markPaid(90001L, "ALIPAY")).thenReturn(1);
        DmsShopMember member = new DmsShopMember();
        member.setUserId(80001L);
        member.setTeamOptIn(1);
        when(memberDao.selectByUserId(80001L)).thenReturn(member);
        when(orderItemDao.selectByOrderId(90001L)).thenReturn(List.of());
        when(authService.activateMember(eq(80001L), eq(1), contains("完成首笔有效支付订单"))).thenReturn(activated);
        when(agentDao.selectByUserId(80001L)).thenReturn(null, agent);
        when(auditService.getOrderFinanceDetail(90001L)).thenReturn(new OrderFinanceDetailVO());
        when(afterSaleDao.selectByOrderId(90001L)).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));

        shopService.markOrderPaid(90001L, "ALIPAY");

        verify(authService).activateMember(eq(80001L), eq(1), contains("完成首笔有效支付订单"));
        verify(productDao, never()).selectById(anyLong());
        verify(orderDao).updateAgentId(90001L, 70001L);
        verify(relationSnapshotService).capture(order);
        verify(performanceService).recordOrderPerformance(eq(90001L), eq("FIRST-ORDER-90001"),
                eq(new BigDecimal("299.00")), eq(1), eq(80001L), any());
    }

    @Test
    void mixedBonusItemsAllocateOrderDiscountByEligibleAmount() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(90003L);
        order.setOrderNo("MIXED-BONUS-90003");
        order.setTenantId(1L);
        order.setUserId(80003L);
        order.setStatus(0);
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setDiscountAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("900.00"));

        DmsShopOrderItem bonusItem = new DmsShopOrderItem();
        bonusItem.setTeamBonusMode("STANDARD");
        bonusItem.setTotalAmount(new BigDecimal("100.00"));
        bonusItem.setQuantity(1);
        DmsShopOrderItem ordinaryItem = new DmsShopOrderItem();
        ordinaryItem.setTeamBonusMode("NONE");
        ordinaryItem.setTotalAmount(new BigDecimal("900.00"));
        ordinaryItem.setQuantity(9);

        DmsShopMember member = new DmsShopMember();
        member.setUserId(80003L);
        member.setTeamOptIn(1);
        AgentInfoVO activated = new AgentInfoVO();
        activated.setId(70003L);
        DmsAgent agent = new DmsAgent();
        agent.setId(70003L);
        agent.setUserId(80003L);
        agent.setAgentName("混合奖金会员");
        agent.setStatus(1);

        when(orderDao.selectByIdForUpdate(90003L)).thenReturn(order);
        when(orderDao.selectById(90003L)).thenReturn(order);
        when(orderDao.markPaid(90003L, "BALANCE")).thenReturn(1);
        when(orderItemDao.selectByOrderId(90003L)).thenReturn(List.of(bonusItem, ordinaryItem));
        when(memberDao.selectByUserId(80003L)).thenReturn(member);
        when(authService.activateMember(eq(80003L), eq(1), anyString())).thenReturn(activated);
        when(agentDao.selectByUserId(80003L)).thenReturn(null, agent);
        when(auditService.getOrderFinanceDetail(90003L)).thenReturn(new OrderFinanceDetailVO());
        when(afterSaleDao.selectByOrderId(90003L)).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));

        shopService.markOrderPaid(90003L, "BALANCE");

        verify(performanceService).recordOrderPerformance(eq(90003L), eq("MIXED-BONUS-90003"),
                eq(new BigDecimal("90.00")), eq(1), eq(80003L), any());
        verify(commissionService).calculateAndRecordCommission(eq(1L), eq(90003L), eq("MIXED-BONUS-90003"),
                eq(new BigDecimal("90.00")), eq(80003L), eq("混合奖金会员"));
    }

    @Test
    void publicShoppingAccountNeverEntersTeamBonusOnPayment() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(90002L);
        order.setOrderNo("PUBLIC-ORDER-90002");
        order.setTenantId(1L);
        order.setUserId(80002L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("199.00"));
        DmsShopMember member = new DmsShopMember();
        member.setUserId(80002L);
        member.setTeamOptIn(0);

        when(orderDao.selectByIdForUpdate(90002L)).thenReturn(order);
        when(orderDao.selectById(90002L)).thenReturn(order);
        when(orderDao.markPaid(90002L, "ALIPAY")).thenReturn(1);
        when(memberDao.selectByUserId(80002L)).thenReturn(member);
        when(auditService.getOrderFinanceDetail(90002L)).thenReturn(new OrderFinanceDetailVO());
        when(afterSaleDao.selectByOrderId(90002L)).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));

        shopService.markOrderPaid(90002L, "ALIPAY");

        verify(authService, never()).activateMember(anyLong(), anyInt(), anyString());
        verify(orderDao, never()).updateAgentId(anyLong(), anyLong());
        verify(relationSnapshotService, never()).capture(any());
        verify(performanceService, never()).recordOrderPerformance(anyLong(), anyString(), any(), anyInt(), anyLong(), any());
        verify(commissionService, never()).calculateAndRecordCommission(anyLong(), anyLong(), anyString(), any(), anyLong(), anyString());
        verify(auditService).refreshOrderFinance(90002L, "PUBLIC-ORDER-90002", new BigDecimal("199.00"));
        verify(orderBalanceAllocationService).prepareForOrder(90002L);
    }

    @Test
    void disabledModeKeepsInvitationButDoesNotOpenPromotionOnPayment() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(90004L);
        order.setOrderNo("DISABLED-ORDER-90004");
        order.setTenantId(1L);
        order.setUserId(80004L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("199.00"));
        DmsShopMember member = new DmsShopMember();
        member.setUserId(80004L);
        member.setInviterId(70004L);
        member.setTeamOptIn(1);
        DmsTenant tenant = new DmsTenant();
        tenant.setPromotionJoinMode("DISABLED");

        when(orderDao.selectByIdForUpdate(90004L)).thenReturn(order);
        when(orderDao.selectById(90004L)).thenReturn(order);
        when(orderDao.markPaid(90004L, "ALIPAY")).thenReturn(1);
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        when(memberDao.selectByUserId(80004L)).thenReturn(member);
        when(auditService.getOrderFinanceDetail(90004L)).thenReturn(new OrderFinanceDetailVO());
        when(afterSaleDao.selectByOrderId(90004L)).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));

        shopService.markOrderPaid(90004L, "ALIPAY");

        verify(authService, never()).activateMember(anyLong(), anyInt(), anyString());
        verify(relationSnapshotService, never()).capture(any());
        verify(performanceService, never()).recordOrderPerformance(anyLong(), anyString(), any(), anyInt(), anyLong(), any());
        verify(auditService).refreshOrderFinance(90004L, "DISABLED-ORDER-90004", new BigDecimal("199.00"));
    }

    @Test
    void disabledJoinModeDoesNotRemoveAnExistingActiveQualification() {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(90005L);
        order.setOrderNo("ACTIVE-ORDER-90005");
        order.setTenantId(1L);
        order.setUserId(80005L);
        order.setStatus(0);
        order.setPayAmount(new BigDecimal("299.00"));
        DmsShopMember member = new DmsShopMember();
        member.setUserId(80005L);
        member.setTeamOptIn(1);
        DmsTenant tenant = new DmsTenant();
        tenant.setPromotionJoinMode("DISABLED");
        DmsAgent agent = new DmsAgent();
        agent.setId(70005L);
        agent.setUserId(80005L);
        agent.setAgentName("已有资格会员");
        agent.setStatus(1);

        when(orderDao.selectByIdForUpdate(90005L)).thenReturn(order);
        when(orderDao.selectById(90005L)).thenReturn(order);
        when(orderDao.markPaid(90005L, "BALANCE")).thenReturn(1);
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        when(memberDao.selectByUserId(80005L)).thenReturn(member);
        when(agentDao.selectByUserId(80005L)).thenReturn(agent);
        when(auditService.getOrderFinanceDetail(90005L)).thenReturn(new OrderFinanceDetailVO());
        when(afterSaleDao.selectByOrderId(90005L)).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));

        shopService.markOrderPaid(90005L, "BALANCE");

        verify(authService, never()).activateMember(anyLong(), anyInt(), anyString());
        verify(orderDao).updateAgentId(90005L, 70005L);
        verify(relationSnapshotService).capture(order);
        verify(performanceService).recordOrderPerformance(eq(90005L), eq("ACTIVE-ORDER-90005"),
                eq(new BigDecimal("299.00")), eq(1), eq(80005L), any());
    }
}
