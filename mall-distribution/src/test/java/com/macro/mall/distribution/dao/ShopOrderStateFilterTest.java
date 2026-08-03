package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShopOrderStateFilterTest {

    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedOrders() {
        insertOrder(930001L, "FILTER-PAY", 0);
        insertOrder(930002L, "FILTER-SHIP", 1);
        insertOrder(930003L, "FILTER-DONE", 3);
        insertOrder(930004L, "FILTER-AFTER", 3);
        insertOrder(930005L, "FILTER-REFUND", 1);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-PENDING", 930004L, "FILTER-AFTER", 0);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-REFUND", 930005L, "FILTER-REFUND", 1);
    }

    @Test
    void filtersBusinessOrderStatesWithoutMixingRefundsAndPendingAfterSales() {
        assertOrderNos("PENDING_PAYMENT", "FILTER-PAY");
        assertOrderNos("PENDING_SHIPMENT", "FILTER-SHIP");
        assertOrderNos("AFTER_SALE", "FILTER-AFTER");
        assertOrderNos("COMPLETED", "FILTER-DONE");
        assertOrderNos("REFUNDED", "FILTER-REFUND");
    }

    private void insertOrder(long id, String orderNo, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id, order_no, tenant_id, user_id, receiver_name, receiver_phone, receiver_address, status)
                VALUES (?, ?, 1, 1, '测试收货人', '13800000000', '测试地址', ?)
                """, id, orderNo, status);
    }

    private void assertOrderNos(String state, String expectedOrderNo) {
        List<String> orderNos = orderDao.selectList("FILTER-", null, state).stream()
                .map(DmsShopOrder::getOrderNo)
                .toList();
        assertEquals(List.of(expectedOrderNo), orderNos);
    }
}
