package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsFlashSaleActivityDao;
import com.macro.mall.distribution.dao.DmsFlashSaleReservationDao;
import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsFlashSaleReservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlashSaleFoundationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsFlashSaleActivityDao activityDao;
    @Autowired private DmsFlashSaleReservationDao reservationDao;
    @Autowired private ShopAfterSaleService shopAfterSaleService;

    @Test
    void databaseAtomicGuardNeverOversellsActivityStock() {
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_activity
                (id,tenant_id,activity_name,product_id,flash_price,flash_pv,total_stock,available_stock,
                 per_user_limit,start_time,end_time,status,version)
                VALUES (990001,1,'并发库存测试',1,1,0,1,1,1,?,?,1,0)
                """, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));

        assertEquals(1, activityDao.decreaseStock(990001L, 1));
        assertEquals(0, activityDao.decreaseStock(990001L, 1));
        DmsFlashSaleActivity activity = activityDao.selectById(990001L);
        assertEquals(0, activity.getAvailableStock());
    }

    @Test
    void releasedStockNeverExceedsConfiguredTotal() {
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_activity
                (id,tenant_id,activity_name,product_id,flash_price,flash_pv,total_stock,available_stock,
                 per_user_limit,start_time,end_time,status,version)
                VALUES (990002,1,'库存回补测试',1,1,0,2,1,1,?,?,1,0)
                """, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));

        activityDao.increaseStock(990002L, 5);
        assertEquals(2, activityDao.selectById(990002L).getAvailableStock());
    }

    @Test
    void paidReservationTracksPartialRefundWithoutReleasingMemberEligibility() {
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_reservation
                (id,tenant_id,activity_id,user_id,order_id,order_no,quantity,released_quantity,status)
                VALUES (990003,1,990002,1001,880001,'FS_TEST',2,0,'PAID')
                """);

        assertEquals(1, reservationDao.releaseRefundedQuantity(880001L, 1));
        DmsFlashSaleReservation partial = reservationDao.selectByOrderId(880001L);
        assertEquals(1, partial.getReleasedQuantity());
        assertEquals("PARTIAL_REFUND", partial.getStatus());

        assertEquals(1, reservationDao.releaseRefundedQuantity(880001L, 1));
        DmsFlashSaleReservation refunded = reservationDao.selectByOrderId(880001L);
        assertEquals(2, refunded.getReleasedQuantity());
        assertEquals("REFUNDED", refunded.getStatus());
        assertEquals(0, reservationDao.releaseRefundedQuantity(880001L, 1));
    }

    @Test
    void cancellingPaidPendingShipmentRestoresFlashStockExactlyOnce() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                (id,user_id,phone,login_account,password_hash,nickname,status)
                VALUES (990010,990010,'13900009010','flash_cancel_member','hash','秒杀取消测试会员',1)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,user_id,receiver_name,receiver_phone,receiver_address,total_amount,
                 freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,business_source_id,
                 status,pay_type,pay_time)
                VALUES (990010,'FLASH-CANCEL-ORDER',1,990010,'测试会员','13900009010','湖南省长沙市测试地址',
                        10,0,0,10,0,5,'FLASH_SALE',990010,1,'SIMULATION',CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (id,order_id,order_no,product_id,product_name,price,quantity,total_amount,pv_value,total_pv,cost_amount,total_cost)
                VALUES (990010,990010,'FLASH-CANCEL-ORDER',1,'轻奢焕活礼盒',10,1,10,0,0,5,5)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_order_finance
                (order_id,order_no,pay_amount,refund_amount,net_pay_amount,product_cost,bonus_amount,
                 company_share_amount,company_profit,risk_status)
                VALUES (990010,'FLASH-CANCEL-ORDER',10,0,10,5,0,0,5,0)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_activity
                (id,tenant_id,activity_name,product_id,flash_price,flash_pv,total_stock,available_stock,
                 per_user_limit,start_time,end_time,status,version)
                VALUES (990010,1,'取消订单库存回补',1,10,0,2,1,1,?,?,1,0)
                """, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_reservation
                (id,tenant_id,activity_id,user_id,order_id,order_no,quantity,released_quantity,status)
                VALUES (990010,1,990010,990010,990010,'FLASH-CANCEL-ORDER',1,0,'PAID')
                """);

        shopAfterSaleService.cancelPendingShipment(990010L, 1L, "测试财务");

        assertEquals(2, activityDao.selectById(990010L).getAvailableStock());
        assertEquals(1, reservationDao.selectByOrderId(990010L).getReleasedQuantity());
        assertEquals("REFUNDED", reservationDao.selectByOrderId(990010L).getStatus());
        assertThrows(RuntimeException.class,
                () -> shopAfterSaleService.cancelPendingShipment(990010L, 1L, "测试财务"));
        assertEquals(2, activityDao.selectById(990010L).getAvailableStock());
    }
}
