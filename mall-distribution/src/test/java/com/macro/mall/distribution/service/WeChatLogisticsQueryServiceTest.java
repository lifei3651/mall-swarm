package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.WeChatWaybillTokenVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import com.macro.mall.distribution.wechat.WeChatPayGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeChatLogisticsQueryServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsOfficialWaybillTokenFromPaidOrderAndShipment() {
        TenantContext.setTenantId(7L);
        WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
        properties.setEnabled(true);
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("secret-for-test");
        WeChatMiniProgramGateway miniGateway = mock(WeChatMiniProgramGateway.class);
        WeChatPayGateway payGateway = mock(WeChatPayGateway.class);
        DmsWechatMiniProgramIdentityDao identityDao = mock(DmsWechatMiniProgramIdentityDao.class);
        DmsShopOrderItemDao itemDao = mock(DmsShopOrderItemDao.class);
        DmsWechatLogisticsFollowTaskDao followTaskDao = mock(DmsWechatLogisticsFollowTaskDao.class);
        WeChatLogisticsQueryService service = new WeChatLogisticsQueryService(
                properties, miniGateway, payGateway, identityDao, itemDao, followTaskDao);
        ReflectionTestUtils.setField(service, "fallbackImageUrl", "https://lingqimall.com/favicon.ico");
        ReflectionTestUtils.setField(service, "publicOrigin", "https://lingqimall.com");

        DmsShopOrder order = new DmsShopOrder();
        order.setId(99L);
        order.setOrderNo("ORDER-99");
        order.setPaymentOrderNo("PAY-99");
        order.setUserId(12L);
        order.setPayType("WECHAT");
        order.setPayTime(LocalDateTime.now());
        order.setReceiverPhone("15500001111");
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setId(66L);
        shipment.setDeliveryCompany("圆通速递");
        shipment.setDeliveryNo("YT1234567890");
        ShopOrderVO detail = new ShopOrderVO();
        detail.setOrder(order);
        detail.setShipments(List.of(shipment));

        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setOpenId("openid-12");
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setProductName("测试商品");
        item.setSkuName("默认规格");
        item.setQuantity(2);
        item.setProductCover("/upload/product.png");
        when(identityDao.selectByUser(eq(7L), any(), eq(12L))).thenReturn(identity);
        when(itemDao.selectByOrderId(99L)).thenReturn(List.of(item));
        when(miniGateway.deliveryCompanies()).thenReturn(List.of(
                new WeChatMiniProgramGateway.DeliveryCompany("YTO", "圆通速递")));
        when(payGateway.query("PAY-99")).thenReturn(new WeChatPayGateway.PaymentResult(
                "SUCCESS", "wx1234567890abcdef", "1900000001", "PAY-99",
                1, "CNY", "openid-12", "4200000000000000000"));
        when(miniGateway.followWaybill(any())).thenReturn(
                new WeChatMiniProgramGateway.WaybillTrackingResult("waybill-token"));

        WeChatWaybillTokenVO result = service.token(detail, 66L);

        assertThat(result.shipmentId()).isEqualTo(66L);
        assertThat(result.waybillToken()).isEqualTo("waybill-token");
        ArgumentCaptor<WeChatMiniProgramGateway.WaybillTrackingCommand> command =
                ArgumentCaptor.forClass(WeChatMiniProgramGateway.WaybillTrackingCommand.class);
        verify(miniGateway).followWaybill(command.capture());
        verify(followTaskDao).completeInteractive(eq(7L), eq(99L), eq(66L), eq(12L),
                any(), any());
        assertThat(command.getValue().deliveryId()).isEqualTo("YTO");
        assertThat(command.getValue().waybillId()).isEqualTo("YT1234567890");
        assertThat(command.getValue().transactionId()).isEqualTo("4200000000000000000");
        assertThat(command.getValue().orderDetailPath()).isEqualTo("pages/order-detail/index?id=99");
        assertThat(command.getValue().goods()).singleElement().satisfies(goods -> {
            assertThat(goods.name()).isEqualTo("测试商品");
            assertThat(goods.description()).isEqualTo("默认规格 × 2");
            assertThat(goods.imageUrl()).isEqualTo("https://lingqimall.com/api/upload/product.png");
        });
    }

}
