package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopOrderVO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantClosedModeTransactionTest {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MerchantService merchantService;
    @Autowired private ShopService shopService;
    @Autowired private ShopAfterSaleService afterSaleService;

    @Test
    void closedModeHidesMerchantProductsButKeepsPlatformProductsAvailable() {
        makeProductOneMerchantOwned();
        assertTrue(shopService.listProducts(1L, null, null, 1, null).stream()
                .anyMatch(product -> Long.valueOf(1L).equals(product.getId())));
        assertNotNull(shopService.getProductDetail(1L));

        closeMerchantMode();

        assertTrue(shopService.listProducts(1L, null, null, 1, null).stream()
                .noneMatch(product -> Long.valueOf(1L).equals(product.getId())));
        assertTrue(shopService.listProductPage(1L, null, null, 1, null, 1, 20).getList().stream()
                .noneMatch(product -> Long.valueOf(1L).equals(product.getId())));
        assertThrows(ApiException.class, () -> shopService.getProductDetail(1L));
        assertNotNull(shopService.getProductDetail(2L));
    }

    @Test
    void closedModeRejectsFreshMerchantOrderWithoutChangingOldPendingOrderOrPlatformCheckout() {
        makeProductOneMerchantOwned();
        DmsShopMember buyer = buyer();
        ShopOrderVO pending = shopService.submitOrder(order(1L, 1L), buyer);
        Long oldOrderId = pending.getOrder().getId();
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dms_shop_order", Long.class);

        closeMerchantMode();

        assertThrows(ApiException.class, () -> shopService.submitOrder(order(1L, 1L), buyer));
        assertEquals(before, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dms_shop_order", Long.class));
        assertEquals(0, shopService.getOrder(oldOrderId).getOrder().getStatus());
        assertEquals(1, shopService.markOrderPaid(oldOrderId, "ALIPAY").getOrder().getStatus());
        assertEquals(1, shopService.getOrder(oldOrderId).getOrder().getStatus());

        ShopOrderVO platformOrder = shopService.submitOrder(order(2L, 3L), buyer);
        assertEquals(0, platformOrder.getOrder().getStatus());
        assertNull(platformOrder.getOrder().getMerchantId());
    }

    @Test
    void closedModePreservesShipmentAndRefundForOrdersPaidBeforeClosure() {
        makeProductOneMerchantOwned();
        DmsShopMember buyer = buyer();
        Long toShip = shopService.submitOrder(order(1L, 1L), buyer).getOrder().getId();
        Long toRefund = shopService.submitOrder(order(1L, 1L), buyer).getOrder().getId();
        shopService.markOrderPaid(toShip, "ALIPAY");
        shopService.markOrderPaid(toRefund, "ALIPAY");
        closeMerchantMode();

        ShopOrderShipDTO shipment = new ShopOrderShipDTO();
        shipment.setDeliveryCompany("顺丰速运");
        shipment.setDeliveryNo("SF-MERCHANT-CLOSED-001");
        shipment.setShipmentQuantity(1);
        assertTrue(shopService.shipOrder(toShip, shipment));
        assertEquals(2, shopService.getOrder(toShip).getOrder().getStatus());

        assertTrue(afterSaleService.cancelPendingShipment(toRefund, 1L, "平台测试"));
        assertTrue(shopService.getOrder(toRefund).getAfterSales().stream()
                .anyMatch(sale -> Integer.valueOf(1).equals(sale.getStatus())));
    }

    @Test
    void closedModeCannotRelistMerchantProductButHistoricalRecordCanStillBeRead() {
        makeProductOneMerchantOwned();
        jdbcTemplate.update("""
                INSERT INTO dms_shop_service_address
                (id,tenant_id,address_type,address_label,contact_name,contact_phone,province,city,district,
                 detail_address,is_default,status)
                VALUES (991711,1,2,'历史商户退货仓','仓库','13900001711','湖南省','长沙市','岳麓区','测试地址',0,1)
                """);
        jdbcTemplate.update("UPDATE dms_shop_product SET status=0,return_address_id=991711 WHERE id=1");
        closeMerchantMode();

        assertThrows(ApiException.class, () -> shopService.updateProductStatus(1L, 1));
        assertEquals(0, shopService.getProduct(1L).getStatus());
        assertTrue(shopService.updateProductStatus(1L, 0));
    }

    private DmsMerchant makeProductOneMerchantOwned() {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo("M-CLOSED-MODE-CHECKOUT");
        merchant.setMerchantName("关闭模式历史商户");
        merchant = merchantService.saveMerchant(merchant);
        jdbcTemplate.update("""
                UPDATE dms_shop_product
                   SET merchant_id=?, merchant_name=?, merchant_review_status='APPROVED',
                       normal_sale_enabled=1, status=1, team_bonus_mode='NONE'
                 WHERE id=1
                """, merchant.getId(), merchant.getMerchantName());
        return merchant;
    }

    private void closeMerchantMode() {
        assertEquals(1, jdbcTemplate.update("UPDATE dms_tenant SET multi_merchant_enabled=0 WHERE id=1"));
    }

    private DmsShopMember buyer() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (991701,991701,'13900001701','merchant_mode_buyer','hash','关闭模式测试会员','MM991701',1,0,0)
                """);
        DmsShopMember buyer = new DmsShopMember();
        buyer.setId(991701L);
        buyer.setUserId(991701L);
        buyer.setPhone("13900001701");
        buyer.setUsername("merchant_mode_buyer");
        buyer.setNickname("关闭模式测试会员");
        buyer.setStatus(1);
        return buyer;
    }

    private ShopOrderSubmitDTO order(long productId, long skuId) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(1);
        ShopOrderSubmitDTO order = new ShopOrderSubmitDTO();
        order.setItems(List.of(item));
        order.setReceiverName("测试收货人");
        order.setReceiverPhone("13900001701");
        order.setReceiverProvince("湖南省");
        order.setReceiverCity("长沙市");
        order.setReceiverDistrict("岳麓区");
        order.setReceiverDetailAddress("测试路一号");
        order.setReceiverAddress("湖南省长沙市岳麓区测试路一号");
        order.setPayType("ALIPAY");
        return order;
    }
}
