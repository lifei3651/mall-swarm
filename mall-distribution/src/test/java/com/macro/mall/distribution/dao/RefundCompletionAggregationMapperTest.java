package com.macro.mall.distribution.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefundCompletionAggregationMapperTest {

    @Autowired private DmsShopAfterSaleItemDao afterSaleItemDao;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void processingRefundReservesQuantityButCannotEnterCompletedAccountingAggregates() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (id, order_id, order_no, product_id, product_name, quantity, total_amount,
                 cost_amount, total_cost, team_bonus_mode)
                VALUES (9955101, 9955001, 'REFUND-AGG-ORDER', 9955201, '退款聚合测试商品', 2, 20, 3, 6, 'STANDARD')
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (id, after_sale_no, order_id, order_no, member_id, user_id, apply_type,
                 refund_amount, product_refund_amount, freight_refund_amount, refund_quantity, status)
                VALUES (9955301, 'REFUND-AGG-DONE', 9955001, 'REFUND-AGG-ORDER', 1, 1, 1,
                        5, 5, 0, 1, 1),
                       (9955302, 'REFUND-AGG-PROCESSING', 9955001, 'REFUND-AGG-ORDER', 1, 1, 1,
                        7, 7, 0, 1, 6)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale_item
                (after_sale_id, order_id, order_item_id, product_id, product_name,
                 refund_quantity, refund_amount, coupon_bonus_refund_amount, coupon_cost_refund_amount)
                VALUES (9955301, 9955001, 9955101, 9955201, '退款聚合测试商品', 1, 5, 5, 3),
                       (9955302, 9955001, 9955101, 9955201, '退款聚合测试商品', 1, 7, 7, 3)
                """);

        // 处理中退款继续占用可退、可发数量，避免重复退款或继续发货。
        assertEquals(2, afterSaleItemDao.sumApprovedQuantityByOrderId(9955001L));
        assertMoney("12.00", afterSaleItemDao.sumApprovedProductRefundByOrderId(9955001L));

        // 净支付/奖金/公司资金和订单关闭只允许读取已完成退款。
        assertEquals(1, afterSaleItemDao.sumCompletedQuantityByOrderId(9955001L));
        assertMoney("5.00", afterSaleItemDao.sumApprovedBonusRefundByOrderId(9955001L));
        assertMoney("3.00", afterSaleItemDao.sumApprovedCostByOrderId(9955001L));
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
