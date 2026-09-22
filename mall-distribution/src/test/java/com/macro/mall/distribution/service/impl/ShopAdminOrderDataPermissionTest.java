package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAgentAccountDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMigrationBaselineDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopAddressDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.vo.AdminMemberProfileVO;
import com.macro.mall.distribution.vo.OrderFinanceDetailVO;
import com.macro.mall.distribution.vo.OrderFinanceVO;
import com.macro.mall.distribution.vo.PerformanceOverviewVO;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAdminOrderDataPermissionTest {

    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderItemDao orderItemDao;
    @Mock private DmsShopOrderShipmentDao orderShipmentDao;
    @Mock private DmsShopAfterSaleDao afterSaleDao;
    @Mock private DmsShopAfterSaleItemDao afterSaleItemDao;
    @Mock private DmsShopAddressDao addressDao;
    @Mock private DmsAgentDao agentDao;
    @Mock private DmsAgentAccountDao accountDao;
    @Mock private DmsMigrationBaselineDao migrationBaselineDao;
    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsTenantDisplayConfigDao displayConfigDao;
    @Mock private DistributionAuditService auditService;
    @Mock private PerformanceService performanceService;
    @Mock private MemberAssetService memberAssetService;
    @Mock private ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    @Mock private ShopAfterSaleTimelinePolicy afterSaleTimelinePolicy;
    @Mock private AdminAuthService adminAuthService;
    @InjectMocks private ShopServiceImpl service;

    private DmsAdminUser admin;
    private DmsShopOrder order;

    @BeforeEach
    void setUp() {
        admin = new DmsAdminUser();
        admin.setId(9101L);
        admin.setPermissions("admin:read,shop:order");
        AdminContext.set(admin);

        order = new DmsShopOrder();
        order.setId(9201L);
        order.setOrderNo("PERMISSION-ORDER-9201");
        order.setTenantId(1L);
        order.setUserId(9301L);
        order.setStatus(1);

    }

    @AfterEach
    void clearContext() {
        AdminContext.clear();
    }

    @Test
    void orderPermissionAloneDoesNotReturnOrLoadFinance() {
        stubOrderList();
        when(adminAuthService.hasPermission(admin, "finance:read")).thenReturn(false);

        ShopOrderVO result = service.listAdminOrders(null, null, null).get(0);

        assertNull(result.getFinance());
        assertNull(result.getOrder().getTotalCost());
        assertNull(result.getOrder().getTotalPv());
        assertNull(result.getOrder().getAgentId());
        assertNull(result.getOrder().getInviteCode());
        assertNull(result.getItems().get(0).getCostAmount());
        assertNull(result.getItems().get(0).getPvValue());
        assertNull(result.getItems().get(0).getTeamBonusMode());
        verify(auditService, never()).getOrderFinanceDetail(anyLong());
        verify(afterSaleDao, never()).selectByOrderId(anyLong());
    }

    @Test
    void orderPermissionAloneOmitsInternalFinanceAndIncentiveFieldsFromJson() throws Exception {
        stubOrderList();

        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(service.listAdminOrders(null, null, null).get(0));

        assertFalse(json.contains("\"totalCost\""));
        assertFalse(json.contains("\"costAmount\""));
        assertFalse(json.contains("\"totalPv\""));
        assertFalse(json.contains("\"pvValue\""));
        assertFalse(json.contains("\"teamBonusMode\""));
        assertFalse(json.contains("\"settlementDelayDays\""));
        assertFalse(json.contains("\"afterSales\""));
    }

    @Test
    void afterSaleFilterIsRejectedBeforeOrderQueryWithoutAfterSalePermission() {
        when(adminAuthService.hasPermission(admin, "shop:aftersale")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.listAdminOrders(null, null, "AFTER_SALE"));

        assertEquals("没有售后查看权限", error.getMessage());
        verifyNoInteractions(orderDao, afterSaleDao);
    }

    @Test
    void refundedFilterIsRejectedBeforeOrderQueryWithoutAfterSalePermission() {
        when(adminAuthService.hasPermission(admin, "shop:aftersale")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.listAdminOrders(null, null, "REFUNDED"));

        assertEquals("没有售后查看权限", error.getMessage());
        verifyNoInteractions(orderDao, afterSaleDao);
    }

    @Test
    void memberPermissionAloneLoadsOnlyBasicProfileSources() throws Exception {
        DmsShopMember member = new DmsShopMember();
        member.setId(9401L);
        member.setUserId(9402L);
        member.setPhone("15500000000");
        member.setUsername("member-9402");
        member.setNickname("最小权限会员");
        when(addressDao.selectByMemberId(member.getId())).thenReturn(List.of());

        AdminMemberProfileVO result = service.getAdminProfile(
                member, false, false, false, false, false);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);

        assertNotNull(result.getMember());
        assertNull(result.getAgent());
        assertNull(result.getAccount());
        assertNull(result.getAssetAccounts());
        assertNull(result.getOrders());
        assertNull(result.getPerformance());
        assertNull(result.getMigrationBaseline());
        assertFalse(json.contains("idCard"));
        assertFalse(json.contains("bankAccount"));
        assertFalse(json.contains("bankName"));
        assertFalse(json.contains("assetAccounts"));
        assertFalse(json.contains("orders"));
        verifyNoInteractions(agentDao, accountDao, migrationBaselineDao,
                performanceService, memberAssetService, orderDao, afterSaleDao, auditService);
    }

    @Test
    void financeReadPermissionReturnsFinanceForAccessibleOrder() {
        stubOrderList();
        when(adminAuthService.hasPermission(admin, "finance:read")).thenReturn(true);
        OrderFinanceVO finance = new OrderFinanceVO();
        finance.setOrderId(order.getId());
        OrderFinanceDetailVO detail = new OrderFinanceDetailVO();
        detail.setFinance(finance);
        when(auditService.getOrderFinanceDetail(order.getId())).thenReturn(detail);

        ShopOrderVO result = service.listAdminOrders(null, null, null).get(0);

        assertNotNull(result.getFinance());
        assertEquals(order.getId(), result.getFinance().getOrderId());
        verify(auditService).getOrderFinanceDetail(order.getId());
    }

    @Test
    void authorizedOrderViewerKeepsFinanceIncentiveAndAfterSaleData() {
        admin.setPermissions("admin:read,shop:order,shop:aftersale,finance:read,commission:manage");
        stubOrderList();
        when(adminAuthService.hasPermission(admin, "finance:read")).thenReturn(true);
        when(adminAuthService.hasPermission(admin, "shop:aftersale")).thenReturn(true);
        when(adminAuthService.hasPermission(admin, "commission:manage")).thenReturn(true);
        when(afterSaleDao.selectByOrderId(order.getId())).thenReturn(List.of());
        OrderFinanceVO finance = new OrderFinanceVO();
        finance.setOrderId(order.getId());
        OrderFinanceDetailVO detail = new OrderFinanceDetailVO();
        detail.setFinance(finance);
        when(auditService.getOrderFinanceDetail(order.getId())).thenReturn(detail);

        ShopOrderVO result = service.listAdminOrders(null, null, null).get(0);

        assertEquals(new BigDecimal("5.55"), result.getOrder().getTotalCost());
        assertEquals(new BigDecimal("8.88"), result.getOrder().getTotalPv());
        assertEquals(new BigDecimal("5.55"), result.getItems().get(0).getCostAmount());
        assertEquals(new BigDecimal("8.88"), result.getItems().get(0).getPvValue());
        assertNotNull(result.getFinance());
        assertNotNull(result.getAfterSales());
        verify(afterSaleDao).selectByOrderId(order.getId());
    }

    @Test
    void fullProfilePermissionStillUsesSafeProjectionForSensitiveAgentFields() throws Exception {
        DmsShopMember member = new DmsShopMember();
        member.setId(9701L);
        member.setUserId(9702L);
        member.setPhone("15500000001");
        member.setNickname("全权限会员");
        DmsAgent agent = new DmsAgent();
        agent.setId(9703L);
        agent.setUserId(member.getUserId());
        agent.setAgentCode("AGENT-9703");
        agent.setAgentName("测试代理");
        agent.setIdCard("430000000000000000");
        agent.setBankName("测试银行");
        agent.setBankAccount("6222000000000000");
        DmsAgentAccount account = new DmsAgentAccount();
        account.setAgentId(agent.getId());
        account.setTotalCommission(new BigDecimal("12.34"));
        when(addressDao.selectByMemberId(member.getId())).thenReturn(List.of());
        when(agentDao.selectByUserId(member.getUserId())).thenReturn(agent);
        when(accountDao.selectByAgentId(agent.getId())).thenReturn(account);
        when(performanceService.getPerformanceOverview(anyLong(), any(), any()))
                .thenReturn(new PerformanceOverviewVO());
        when(migrationBaselineDao.selectByAgentId(agent.getId())).thenReturn(null);
        when(memberAssetService.listAccounts(agent.getId(), member.getUserId())).thenReturn(List.of());

        AdminMemberProfileVO result = service.getAdminProfile(
                member, false, false, true, true, true);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);

        assertNotNull(result.getAgent());
        assertNotNull(result.getAccount());
        assertNotNull(result.getAssetAccounts());
        assertFalse(json.contains("idCard"));
        assertFalse(json.contains("bankAccount"));
        assertFalse(json.contains("bankName"));
        assertFalse(json.contains("ancestorIds"));
    }

    @Test
    void memberOrderListDoesNotDependOnFinanceHydration() {
        AdminContext.clear();
        when(orderDao.selectByUserIdAndState(order.getUserId(), null)).thenReturn(List.of(order));
        stubOrderHydration();
        when(afterSaleDao.selectByOrderId(order.getId())).thenReturn(List.of());

        ShopOrderVO result = service.listOrders(order.getUserId(), null, null).get(0);

        assertNull(result.getFinance());
        verify(auditService, never()).getOrderFinanceDetail(anyLong());
    }

    @Test
    void orderSummarySuppressesAfterSaleCountWithoutAfterSalePermission() {
        ShopOrderStatusSummaryVO stored = summary(4L, 7L);
        when(orderDao.selectAdminWorkSummary(1L, null, false)).thenReturn(stored);
        when(adminAuthService.hasPermission(admin, "shop:aftersale")).thenReturn(false);

        ShopOrderStatusSummaryVO result = service.getAdminOrderWorkSummary();

        assertEquals(4L, result.getPendingShipment());
        assertEquals(0L, result.getAfterSale());
    }

    @Test
    void orderSummaryKeepsAfterSaleCountWithAfterSalePermission() {
        when(orderDao.selectAdminWorkSummary(1L, null, true)).thenReturn(summary(4L, 7L));
        when(adminAuthService.hasPermission(admin, "shop:aftersale")).thenReturn(true);

        ShopOrderStatusSummaryVO result = service.getAdminOrderWorkSummary();

        assertEquals(4L, result.getPendingShipment());
        assertEquals(7L, result.getAfterSale());
    }

    private ShopOrderStatusSummaryVO summary(Long pendingShipment, Long afterSale) {
        ShopOrderStatusSummaryVO summary = new ShopOrderStatusSummaryVO();
        summary.setPendingShipment(pendingShipment);
        summary.setAfterSale(afterSale);
        return summary;
    }

    private void stubOrderList() {
        when(orderDao.selectList(1L, null, null, null, null)).thenReturn(List.of(order));
        stubOrderHydration();
    }

    private void stubOrderHydration() {
        order.setAgentId(9501L);
        order.setInviteCode("INVITE01");
        order.setTotalPv(new BigDecimal("8.88"));
        order.setTotalCost(new BigDecimal("5.55"));
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setId(9601L);
        item.setOrderId(order.getId());
        item.setProductName("权限裁剪商品");
        item.setCostAmount(new BigDecimal("5.55"));
        item.setTotalCost(new BigDecimal("5.55"));
        item.setPvValue(new BigDecimal("8.88"));
        item.setTotalPv(new BigDecimal("8.88"));
        item.setSettlementDelayDays(7);
        item.setTeamBonusMode("STANDARD");
        when(orderItemDao.selectByOrderId(order.getId())).thenReturn(List.of(item));
        when(orderShipmentDao.selectByOrderId(order.getId())).thenReturn(List.of());
        when(afterSaleWindowPolicy.resolve(1L))
                .thenReturn(new ShopAfterSaleWindowPolicy.Window(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7));
    }
}
