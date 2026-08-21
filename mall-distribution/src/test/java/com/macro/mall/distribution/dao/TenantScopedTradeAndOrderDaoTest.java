package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopTrade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantScopedTradeAndOrderDaoTest {

    @Autowired private DmsShopTradeDao tradeDao;
    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedTwoTenants() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_trade
                (id, trade_no, tenant_id, user_id, pay_type, pay_amount, status)
                VALUES (940001, 'TENANT-TRADE-1', 1, 101, 'BALANCE', 10, 0),
                       (940002, 'TENANT-TRADE-2', 2, 202, 'BALANCE', 20, 0)
                """);
        insertOrder(940011L, "TENANT-ORDER-1", 1L, 0);
        insertOrder(940012L, "TENANT-ORDER-2", 2L, 0);
        insertOrder(940013L, "TENANT-SHIP-2", 2L, 1);
        insertOrder(940014L, "TENANT-RECEIVE-2", 2L, 2);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void parentTradeReadsAndWritesStayInsideCurrentTenant() {
        TenantContext.setTenantId(1L);
        assertNotNull(tradeDao.selectById(940001L));
        assertNotNull(tradeDao.selectByIdForUpdate(940001L));
        assertNull(tradeDao.selectById(940002L));
        assertNull(tradeDao.selectByTradeNo("TENANT-TRADE-2"));
        assertNull(tradeDao.selectByTradeNoForUpdate("TENANT-TRADE-2"));
        assertEquals(0, tradeDao.markPaid(940002L, "BALANCE"));
        assertEquals(0, tradeDao.closePending(940002L));

        DmsShopTrade foreignTrade = new DmsShopTrade();
        foreignTrade.setId(940003L);
        foreignTrade.setTradeNo("TENANT-TRADE-INSERT-2");
        foreignTrade.setTenantId(2L);
        foreignTrade.setUserId(202L);
        foreignTrade.setStatus(0);
        assertThrows(IllegalArgumentException.class, () -> tradeDao.insert(foreignTrade));

        TenantContext.setTenantId(2L);
        assertNotNull(tradeDao.selectByTradeNo("TENANT-TRADE-2"));
        assertEquals(1, tradeDao.markPaid(940002L, "BALANCE"));
    }

    @Test
    void defaultOrderListUsesCurrentTenantInsteadOfFixedTenantOne() {
        TenantContext.setTenantId(2L);

        List<String> orderNos = orderDao.selectList("TENANT-ORDER-", null, null).stream()
                .map(DmsShopOrder::getOrderNo)
                .sorted()
                .toList();

        assertEquals(List.of("TENANT-ORDER-2"), orderNos);
    }

    @Test
    void orderWritesCannotChangeAnotherTenantButRemainAvailableToOwnerTenant() {
        TenantContext.setTenantId(1L);
        assertEquals(0, orderDao.markPaid(940012L, "BALANCE"));
        assertEquals(0, orderDao.updateStatus(940012L, 4));
        assertEquals(0, orderDao.updateAgentId(940012L, 88L));
        assertEquals(0, orderDao.updateServiceRemark(940012L, "越权备注"));
        assertEquals(0, orderDao.ship(940013L, "顺丰速运", "SF940013"));
        assertEquals(0, orderDao.confirmReceive(940014L));
        assertEquals(0, orderDao.cancel(940012L));
        assertEquals(0, orderDao.closeAfterSale(940013L));
        assertEquals(0, orderDao.closePending(940012L));

        TenantContext.setTenantId(2L);
        assertEquals(1, orderDao.markPaid(940012L, "BALANCE"));
        assertEquals(1, orderDao.ship(940013L, "顺丰速运", "SF940013"));
        assertEquals(1, orderDao.confirmReceive(940014L));
    }

    private void insertOrder(long id, String orderNo, long tenantId, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id, order_no, tenant_id, user_id, receiver_name, receiver_phone, receiver_address, status)
                VALUES (?, ?, ?, 1, '测试收货人', '13800000000', '测试地址', ?)
                """, id, orderNo, tenantId, status);
    }
}
