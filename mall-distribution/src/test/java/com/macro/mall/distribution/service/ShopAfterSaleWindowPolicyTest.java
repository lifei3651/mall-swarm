package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.impl.ShopAfterSaleWindowPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopAfterSaleWindowPolicyTest {

    @Mock private DmsTenantDao tenantDao;
    private ShopAfterSaleWindowPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ShopAfterSaleWindowPolicy(tenantDao);
    }

    @Test
    void receivedModeKeepsEntryOpenBeforeReceiptAndStartsAtReceipt() {
        DmsTenant tenant = tenant(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 7);
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        DmsShopOrder order = order();

        assertNull(policy.deadline(order));
        assertFalse(policy.isExpired(order, LocalDateTime.of(2026, 8, 30, 0, 0)));

        order.setReceiveTime(LocalDateTime.of(2026, 8, 12, 10, 0));
        assertEquals(LocalDateTime.of(2026, 8, 19, 10, 0), policy.deadline(order));
        assertFalse(policy.isExpired(order, LocalDateTime.of(2026, 8, 19, 9, 59)));
        assertTrue(policy.isExpired(order, LocalDateTime.of(2026, 8, 19, 10, 0)));
        assertEquals("签收后7天", policy.label(order));
    }

    @Test
    void orderCreatedModePreservesTheLegacyBusinessRule() {
        when(tenantDao.selectById(1L)).thenReturn(tenant(ShopAfterSaleWindowPolicy.MODE_ORDER_CREATED, 10));
        DmsShopOrder order = order();

        assertEquals(LocalDateTime.of(2026, 8, 11, 9, 0), policy.deadline(order));
        assertEquals("下单后10天", policy.label(order));
    }

    @Test
    void zeroDaysDisablesCustomerSelfServiceBeforeAndAfterReceipt() {
        when(tenantDao.selectById(1L)).thenReturn(tenant(ShopAfterSaleWindowPolicy.MODE_RECEIVED, 0));
        DmsShopOrder order = order();

        assertEquals(order.getCreateTime(), policy.deadline(order));
        assertTrue(policy.isExpired(order, LocalDateTime.of(2026, 8, 1, 9, 0)));
        assertEquals("客户自助售后入口已关闭", policy.label(order));

        order.setReceiveTime(LocalDateTime.of(2026, 8, 12, 10, 0));
        assertTrue(policy.isExpired(order, LocalDateTime.of(2026, 8, 12, 10, 0)));
    }

    private DmsTenant tenant(String mode, int days) {
        DmsTenant tenant = new DmsTenant();
        tenant.setAfterSaleWindowMode(mode);
        tenant.setAfterSaleWindowDays(days);
        return tenant;
    }

    private DmsShopOrder order() {
        DmsShopOrder order = new DmsShopOrder();
        order.setTenantId(1L);
        order.setCreateTime(LocalDateTime.of(2026, 8, 1, 9, 0));
        return order;
    }
}
