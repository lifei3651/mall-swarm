package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
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
        insertOrder(930006L, "FILTER-SHIP-AFTER", 1);
        insertOrder(930007L, "FILTER-SHIP-CANCELED", 1);
        insertOrder(930008L, "FILTER-RECEIVE", 2);
        insertOrder(930009L, "FILTER-RECEIVE-AFTER", 2);
        insertOrder(930010L, "FILTER-RECEIVE-CANCELED", 2);
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
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-SHIP-AFTER", 930006L, "FILTER-SHIP-AFTER", 0);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-SHIP-CANCELED", 930007L, "FILTER-SHIP-CANCELED", 3);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-RECEIVE-AFTER", 930009L, "FILTER-RECEIVE-AFTER", 0);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, "AS-FILTER-RECEIVE-CANCELED", 930010L, "FILTER-RECEIVE-CANCELED", 3);
    }

    @Test
    void filtersBusinessOrderStatesWithoutMixingRefundsAndPendingAfterSales() {
        assertOrderNos("PENDING_PAYMENT", "FILTER-PAY");
        assertOrderNos("PENDING_SHIPMENT", "FILTER-REFUND", "FILTER-SHIP", "FILTER-SHIP-CANCELED");
        assertUserOrderNos("PENDING_SHIPMENT", "FILTER-REFUND", "FILTER-SHIP", "FILTER-SHIP-CANCELED");
        assertUserOrderNos("PENDING_RECEIPT", "FILTER-RECEIVE", "FILTER-RECEIVE-CANCELED");
        assertOrderNos("AFTER_SALE", "FILTER-AFTER", "FILTER-RECEIVE-AFTER", "FILTER-SHIP-AFTER");
        assertOrderNos("COMPLETED", "FILTER-DONE");
        assertOrderNos("REFUNDED", "FILTER-REFUND");
    }

    @Test
    void summaryBadgeCountsOnlyAfterSalesThatStillNeedAction() {
        ShopOrderStatusSummaryVO summary = orderDao.selectStatusSummary(1L);

        assertEquals(3L, summary.getAfterSale());
        assertEquals(3L, summary.getPendingShipment());
        assertEquals(2L, summary.getPendingReceipt());
    }

    @Test
    void everyOpenAfterSaleStateIsExcludedFromReceiptListBadgeAndDatabaseConfirmation() {
        int[] openStatuses = {0, 4, 5, 6, 7, 8};
        for (int index = 0; index < openStatuses.length; index++) {
            long orderId = 930100L + index;
            String orderNo = "FILTER-OPEN-" + openStatuses[index];
            insertOrder(orderId, orderNo, 2);
            insertAfterSale("AS-" + orderNo, orderId, orderNo, openStatuses[index]);
            assertEquals(0, orderDao.confirmReceive(orderId), "进行中售后状态不能绕过数据库确认收货门禁");
            assertEquals(2, orderDao.selectById(orderId).getStatus());
        }

        assertUserOrderNosWithoutPrefix("PENDING_RECEIPT", "FILTER-OPEN-");
        ShopOrderStatusSummaryVO summary = orderDao.selectStatusSummary(1L);
        assertEquals(2L, summary.getPendingReceipt());
        assertEquals(9L, summary.getAfterSale());
    }

    @Test
    void terminalAfterSaleStatesRestoreReceiptListBadgeAndDatabaseConfirmation() {
        int[] terminalStatuses = {1, 2, 3};
        for (int index = 0; index < terminalStatuses.length; index++) {
            long orderId = 930200L + index;
            String orderNo = "FILTER-CLOSED-" + terminalStatuses[index];
            insertOrder(orderId, orderNo, 2);
            insertAfterSale("AS-" + orderNo, orderId, orderNo, terminalStatuses[index]);
        }

        assertUserOrderNosWithPrefix("PENDING_RECEIPT", "FILTER-CLOSED-",
                "FILTER-CLOSED-1", "FILTER-CLOSED-2", "FILTER-CLOSED-3");
        ShopOrderStatusSummaryVO summary = orderDao.selectStatusSummary(1L);
        assertEquals(5L, summary.getPendingReceipt());
        assertEquals(3L, summary.getAfterSale());
        assertEquals(1, orderDao.confirmReceive(930200L));
        assertEquals(3, orderDao.selectById(930200L).getStatus());
    }

    @Test
    void adminWorkSummarySeparatesShipmentAndAfterSaleQueuesByTenant() {
        insertOrder(930011L, "FILTER-OTHER-TENANT", 1, 2L);

        ShopOrderStatusSummaryVO summary = orderDao.selectAdminWorkSummary(1L);

        assertEquals(3L, summary.getPendingShipment());
        assertEquals(3L, summary.getAfterSale());
    }

    @Test
    void keywordSearchTreatsSqlMetacharactersAsData() {
        List<DmsShopOrder> orders = orderDao.selectList("' OR 1=1 --", null, null);

        assertEquals(List.of(), orders);
    }

    private void insertOrder(long id, String orderNo, int status) {
        insertOrder(id, orderNo, status, 1L);
    }

    private void insertOrder(long id, String orderNo, int status, long tenantId) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id, order_no, tenant_id, user_id, receiver_name, receiver_phone, receiver_address, status)
                VALUES (?, ?, ?, 1, '测试收货人', '13800000000', '测试地址', ?)
                """, id, orderNo, tenantId, status);
    }

    private void insertAfterSale(String afterSaleNo, long orderId, String orderNo, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (after_sale_no, order_id, order_no, member_id, user_id, refund_amount,
                 product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (?, ?, ?, 1, 1, 10, 10, 0, 1, ?)
                """, afterSaleNo, orderId, orderNo, status);
    }

    private void assertOrderNos(String state, String... expectedOrderNos) {
        List<String> orderNos = orderDao.selectList("FILTER-", null, state).stream()
                .map(DmsShopOrder::getOrderNo)
                .sorted()
                .toList();
        assertEquals(List.of(expectedOrderNos).stream().sorted().toList(), orderNos);
    }

    private void assertUserOrderNos(String state, String... expectedOrderNos) {
        List<String> orderNos = orderDao.selectByUserIdAndState(1L, state).stream()
                .map(DmsShopOrder::getOrderNo)
                .filter(orderNo -> orderNo.startsWith("FILTER-"))
                .sorted()
                .toList();
        assertEquals(List.of(expectedOrderNos).stream().sorted().toList(), orderNos);
    }

    private void assertUserOrderNosWithoutPrefix(String state, String excludedPrefix) {
        List<String> orderNos = orderDao.selectByUserIdAndState(1L, state).stream()
                .map(DmsShopOrder::getOrderNo)
                .filter(orderNo -> orderNo.startsWith(excludedPrefix))
                .toList();
        assertEquals(List.of(), orderNos);
    }

    private void assertUserOrderNosWithPrefix(String state, String prefix, String... expectedOrderNos) {
        List<String> orderNos = orderDao.selectByUserIdAndState(1L, state).stream()
                .map(DmsShopOrder::getOrderNo)
                .filter(orderNo -> orderNo.startsWith(prefix))
                .sorted()
                .toList();
        assertEquals(List.of(expectedOrderNos).stream().sorted().toList(), orderNos);
    }
}
