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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlashSaleFoundationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsFlashSaleActivityDao activityDao;
    @Autowired private DmsFlashSaleReservationDao reservationDao;

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
}
