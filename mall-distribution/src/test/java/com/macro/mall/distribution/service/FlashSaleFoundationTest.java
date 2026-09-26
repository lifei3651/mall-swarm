package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsFlashSaleActivityDao;
import com.macro.mall.distribution.dao.DmsFlashSaleReservationDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dto.FlashSaleActivitySaveDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsFlashSaleReservation;
import com.macro.mall.distribution.entity.DmsShopMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlashSaleFoundationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsFlashSaleActivityDao activityDao;
    @Autowired private DmsFlashSaleReservationDao reservationDao;
    @Autowired private DmsShopAfterSaleDao afterSaleDao;
    @Autowired private ShopAfterSaleService shopAfterSaleService;
    @Autowired private FlashSaleService flashSaleService;

    @Test
    void closedFlashSaleModuleCannotCreateOrReactivateButCanStopHistoricalActivity() {
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_activity
                (id,tenant_id,activity_name,product_id,flash_price,flash_pv,total_stock,available_stock,
                 per_user_limit,start_time,end_time,status,version)
                VALUES (990020,1,'关闭态旧活动',1,1,0,1,1,1,?,?,1,0)
                """, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        jdbcTemplate.update("UPDATE dms_tenant SET flash_sale_enabled=0 WHERE id=1");

        assertThrows(RuntimeException.class, () -> flashSaleService.save(null, new FlashSaleActivitySaveDTO()));
        assertThrows(RuntimeException.class, () -> flashSaleService.updateStatus(990020L, 1));
        assertEquals(1, activityDao.selectById(990020L).getStatus());
        assertEquals(true, flashSaleService.updateStatus(990020L, 2));
        assertEquals(2, activityDao.selectById(990020L).getStatus());
    }

    @Test
    void platformOnlyModeHidesMerchantFlashSaleAndRejectsSubmitBeforeReservingStock() {
        jdbcTemplate.update("UPDATE dms_tenant SET flash_sale_enabled=1,multi_merchant_enabled=0 WHERE id=1");
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=990031,merchant_name='历史商户' WHERE id=1");
        insertActivity(990031L, 1L, 1);
        insertActivity(990032L, 2L, 1);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                (id,user_id,phone,login_account,password_hash,nickname,status)
                VALUES (990031,990031,'13900009031','flash_platform_only_member','hash','秒杀商户关闭测试会员',1)
                """);

        assertEquals(List.of(990032L), flashSaleService.listFront().stream()
                .map(item -> item.getActivity().getId()).toList());
        assertTrue(flashSaleService.listAdmin(1).stream()
                .anyMatch(item -> Long.valueOf(990031L).equals(item.getActivity().getId())));

        DmsShopMember member = new DmsShopMember();
        member.setId(990031L);
        member.setUserId(990031L);
        member.setStatus(1);
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO order = new ShopOrderSubmitDTO();
        order.setItems(List.of(item));
        RuntimeException rejected = assertThrows(RuntimeException.class,
                () -> flashSaleService.submit(990031L, order, member));
        assertTrue(rejected.getMessage().contains("仅支持平台自营"));
        assertEquals(1, activityDao.selectById(990031L).getAvailableStock());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dms_flash_sale_reservation WHERE activity_id=990031", Integer.class));
    }

    @Test
    void platformOnlyModeRejectsMerchantFlashSaleReactivationButAllowsPlatformActivity() {
        jdbcTemplate.update("UPDATE dms_tenant SET flash_sale_enabled=1,multi_merchant_enabled=0 WHERE id=1");
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=990033,merchant_name='历史商户' WHERE id=1");
        insertActivity(990033L, 1L, 0);
        insertActivity(990034L, 2L, 0);

        RuntimeException rejected = assertThrows(RuntimeException.class,
                () -> flashSaleService.updateStatus(990033L, 1));
        assertTrue(rejected.getMessage().contains("仅支持平台自营"));
        assertEquals(0, activityDao.selectById(990033L).getStatus());

        assertTrue(flashSaleService.updateStatus(990034L, 1));
        assertEquals(1, activityDao.selectById(990034L).getStatus());
        assertEquals(List.of(990034L), flashSaleService.listFront().stream()
                .map(item -> item.getActivity().getId()).toList());
    }

    private void insertActivity(Long id, Long productId, int status) {
        jdbcTemplate.update("""
                INSERT INTO dms_flash_sale_activity
                (id,tenant_id,activity_name,product_id,flash_price,flash_pv,total_stock,available_stock,
                 per_user_limit,start_time,end_time,status,version)
                VALUES (?,1,'多商户关闭态秒杀',?,1,0,1,1,1,?,?,?,0)
                """, id, productId, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(10), status);
    }

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
                        10,2,0,12,0,5,'FLASH_SALE',990010,1,'SIMULATION',CURRENT_TIMESTAMP)
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
                VALUES (990010,'FLASH-CANCEL-ORDER',12,0,12,5,0,0,7,0)
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

        // 模块停止新交易后，旧订单的取消/退款与资格库存回补仍必须完成。
        jdbcTemplate.update("UPDATE dms_tenant SET flash_sale_enabled=0 WHERE id=1");

        shopAfterSaleService.cancelPendingShipment(990010L, 1L, "测试财务");

        assertEquals(0, new BigDecimal("12.00").compareTo(
                afterSaleDao.selectByOrderId(990010L).get(0).getRefundAmount()));
        assertEquals(0, new BigDecimal("2.00").compareTo(
                afterSaleDao.selectByOrderId(990010L).get(0).getFreightRefundAmount()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT net_pay_amount FROM dms_order_finance WHERE order_id = 990010",
                BigDecimal.class).compareTo(BigDecimal.ZERO));
        assertEquals(2, activityDao.selectById(990010L).getAvailableStock());
        assertEquals(1, reservationDao.selectByOrderId(990010L).getReleasedQuantity());
        assertEquals("REFUNDED", reservationDao.selectByOrderId(990010L).getStatus());
        assertThrows(RuntimeException.class,
                () -> shopAfterSaleService.cancelPendingShipment(990010L, 1L, "测试财务"));
        assertEquals(2, activityDao.selectById(990010L).getAvailableStock());
    }
}
