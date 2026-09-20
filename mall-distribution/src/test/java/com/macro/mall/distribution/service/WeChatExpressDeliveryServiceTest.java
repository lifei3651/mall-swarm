package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.WechatExpressOrderDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.vo.WechatExpressShipmentVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeChatExpressDeliveryServiceTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        com.macro.mall.distribution.security.AdminContext.clear();
    }

    @Test
    void createsOfficialWaybillThenUsesExistingShipmentChain() {
        TenantContext.setTenantId(7L);
        WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
        properties.setEnabled(true);
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("test-secret");
        WeChatMiniProgramGateway gateway = mock(WeChatMiniProgramGateway.class);
        DmsShopOrderDao orderDao = mock(DmsShopOrderDao.class);
        DmsShopOrderItemDao itemDao = mock(DmsShopOrderItemDao.class);
        DmsShopAfterSaleDao afterSaleDao = mock(DmsShopAfterSaleDao.class);
        DmsShopAfterSaleItemDao afterSaleItemDao = mock(DmsShopAfterSaleItemDao.class);
        DmsShopOrderShipmentDao shipmentDao = mock(DmsShopOrderShipmentDao.class);
        DmsShopServiceAddressDao addressDao = mock(DmsShopServiceAddressDao.class);
        DmsWechatMiniProgramIdentityDao identityDao = mock(DmsWechatMiniProgramIdentityDao.class);
        DmsWechatExpressOrderDao expressDao = mock(DmsWechatExpressOrderDao.class);
        DmsMerchantDao merchantDao = mock(DmsMerchantDao.class);
        OrderShipmentService shipmentService = mock(OrderShipmentService.class);
        WeChatExpressDeliveryService service = new WeChatExpressDeliveryService(properties, gateway, orderDao,
                itemDao, afterSaleDao, afterSaleItemDao, shipmentDao, addressDao, identityDao, expressDao,
                merchantDao, shipmentService);
        ReflectionTestUtils.setField(service, "fallbackImageUrl", "https://lingqimall.com/favicon.ico");
        ReflectionTestUtils.setField(service, "publicOrigin", "https://lingqimall.com");

        DmsShopOrder order = new DmsShopOrder();
        order.setId(99L); order.setTenantId(7L); order.setOrderNo("ORDER99"); order.setUserId(12L);
        order.setStatus(1); order.setPayType("WECHAT"); order.setPayTime(LocalDateTime.now());
        order.setReceiverName("收件人"); order.setReceiverPhone("15500001111");
        order.setReceiverProvince("湖南省"); order.setReceiverCity("长沙市");
        order.setReceiverDistrict("长沙县"); order.setReceiverDetailAddress("测试路1号");
        when(orderDao.selectById(99L)).thenReturn(order);
        when(itemDao.sumQuantityByOrderId(99L)).thenReturn(2);
        when(afterSaleItemDao.sumApprovedQuantityByOrderId(99L)).thenReturn(0);
        when(shipmentDao.sumQuantityByOrderId(99L)).thenReturn(0);

        DmsShopServiceAddress sender = new DmsShopServiceAddress();
        sender.setContactName("发件人"); sender.setContactPhone("13800138000");
        sender.setProvince("湖南省"); sender.setCity("长沙市"); sender.setDistrict("长沙县");
        sender.setDetailAddress("仓库路2号");
        when(addressDao.selectDefaultForMerchant(7L, null, 1)).thenReturn(sender);
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setOpenId("openid-12");
        when(identityDao.selectByUser(eq(7L), anyString(), eq(12L))).thenReturn(identity);
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setProductName("测试商品"); item.setSkuName("默认规格"); item.setQuantity(2);
        item.setProductCover("/upload/product.png");
        when(itemDao.selectByOrderId(99L)).thenReturn(List.of(item));

        var expressAccount = new WeChatMiniProgramGateway.ExpressAccount("BIZ-1", "YTO", "仓库账号", 0,
                50, List.of(new WeChatMiniProgramGateway.ExpressServiceType(0, "标准快递")));
        when(gateway.expressAccounts()).thenReturn(List.of(expressAccount));
        when(gateway.expressDeliveryCompanies()).thenReturn(List.of(
                new WeChatMiniProgramGateway.ExpressDeliveryCompany("YTO", "圆通速递", false, null,
                        List.of(new WeChatMiniProgramGateway.ExpressServiceType(0, "标准快递")))));

        DmsWechatExpressOrder record = new DmsWechatExpressOrder();
        record.setId(88L); record.setTenantId(7L); record.setOrderId(99L); record.setUserId(12L);
        record.setRequestKey("1234567890abcdef"); record.setExpressOrderNo("LQ-ORDER99-ABCDEF123456");
        record.setDeliveryId("YTO"); record.setDeliveryName("圆通速递"); record.setBizId("BIZ-1");
        record.setServiceType(0); record.setServiceName("标准快递"); record.setShipmentQuantity(2);
        record.setPackageCount(1); record.setWeight(new BigDecimal("1.00"));
        record.setPackageLength(new BigDecimal("20.0")); record.setPackageWidth(new BigDecimal("15.0"));
        record.setPackageHeight(new BigDecimal("10.0")); record.setStatus("PENDING");
        when(expressDao.selectByRequest(7L, 99L, "1234567890abcdef")).thenReturn(record);
        when(gateway.createExpressOrder(any())).thenReturn(new WeChatMiniProgramGateway.ExpressOrderResult(
                record.getExpressOrderNo(), "YTO", "YT123456789", null, null));
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setId(77L); shipment.setOrderId(99L); shipment.setDeliveryCompany("圆通速递");
        shipment.setDeliveryNo("YT123456789");
        when(shipmentDao.selectByOrderAndTracking(99L, "圆通速递", "YT123456789")).thenReturn(shipment);
        when(shipmentService.shipOrder(eq(99L), any())).thenReturn(true);

        WechatExpressOrderDTO dto = dto();
        WechatExpressShipmentVO result = service.create(99L, dto);

        assertThat(result.deliveryNo()).isEqualTo("YT123456789");
        assertThat(result.shipmentId()).isEqualTo(77L);
        ArgumentCaptor<WeChatMiniProgramGateway.ExpressOrderCommand> command =
                ArgumentCaptor.forClass(WeChatMiniProgramGateway.ExpressOrderCommand.class);
        verify(gateway).createExpressOrder(command.capture());
        assertThat(command.getValue().sender().phone()).isEqualTo("13800138000");
        assertThat(command.getValue().receiver().address()).isEqualTo("测试路1号");
        assertThat(command.getValue().shopItems()).singleElement().satisfies(goods ->
                assertThat(goods.imageUrl()).isEqualTo("https://lingqimall.com/api/upload/product.png"));
        verify(expressDao).markWaybill(7L, 88L, "YT123456789");
        verify(expressDao).markSuccess(7L, 88L, 77L);
    }

    private WechatExpressOrderDTO dto() {
        WechatExpressOrderDTO dto = new WechatExpressOrderDTO();
        dto.setRequestKey("1234567890abcdef"); dto.setDeliveryId("YTO"); dto.setBizId("BIZ-1");
        dto.setServiceType(0); dto.setShipmentQuantity(2); dto.setPackageCount(1);
        dto.setWeight(new BigDecimal("1")); dto.setPackageLength(new BigDecimal("20"));
        dto.setPackageWidth(new BigDecimal("15")); dto.setPackageHeight(new BigDecimal("10"));
        return dto;
    }
}
