package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "shop.payment.simulation-enabled=false")
@ActiveProfiles("test")
@Transactional
class NewOrderPaymentChannelIntegrationTest {
    @Autowired private ShopService shopService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AlipayConfig alipay;
    @Autowired private WeChatPayProperties wechat;
    @Autowired private WeChatMiniProgramProperties miniProgram;

    @Test
    void disabledChannelsCannotCreateOrderButOldPaymentCallbackStillCompletes() {
        alipay.setEnabled(false);
        wechat.setEnabled(false);
        miniProgram.setEnabled(false);
        DmsShopMember buyer = buyer();
        long initialOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dms_shop_order", Long.class);
        int initialStock = jdbcTemplate.queryForObject("SELECT stock FROM dms_shop_sku WHERE id=1", Integer.class);

        assertThrows(ApiException.class, () -> shopService.submitOrder(order("ALIPAY"), buyer));
        assertThrows(ApiException.class, () -> shopService.submitOrder(order("WECHAT"), buyer));
        assertThrows(ApiException.class, () -> shopService.submitOrder(order(null), buyer));
        assertThrows(ApiException.class, () -> shopService.quoteFreight(order("ALIPAY"), buyer));
        assertThrows(ApiException.class, () -> shopService.quoteFreight(order("WECHAT"), buyer));
        assertEquals(initialOrders, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dms_shop_order", Long.class));
        assertEquals(initialStock, jdbcTemplate.queryForObject("SELECT stock FROM dms_shop_sku WHERE id=1", Integer.class));

        configureWechat();
        shopService.quoteFreight(order("WECHAT"), buyer);
        wechat.setEnabled(false);
        assertThrows(ApiException.class, () -> shopService.submitOrder(order("WECHAT"), buyer));
        wechat.setEnabled(true);
        ShopOrderVO wechatOrder = shopService.submitOrder(order("WECHAT"), buyer);
        assertEquals(0, wechatOrder.getOrder().getStatus());
        assertThrows(ApiException.class, () -> shopService.submitOrder(order("ALIPAY"), buyer));

        configureAlipay();
        ShopOrderVO pending = shopService.submitOrder(order("ALIPAY"), buyer);
        alipay.setEnabled(false);
        assertEquals(1, shopService.markOrderPaid(pending.getOrder().getId(), "ALIPAY").getOrder().getStatus());
        wechat.setEnabled(false);
        assertEquals(1, shopService.markOrderPaid(wechatOrder.getOrder().getId(), "WECHAT").getOrder().getStatus());
    }

    private DmsShopMember buyer() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (992001,992001,'13900002001','payment_gate_buyer','hash','支付门禁测试','PG992001',1,0,0)
                """);
        DmsShopMember buyer = new DmsShopMember();
        buyer.setId(992001L);
        buyer.setUserId(992001L);
        buyer.setStatus(1);
        return buyer;
    }

    private ShopOrderSubmitDTO order(String payType) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setItems(List.of(item));
        dto.setReceiverName("支付门禁测试");
        dto.setReceiverPhone("13900002001");
        dto.setReceiverProvince("湖南省");
        dto.setReceiverCity("长沙市");
        dto.setReceiverDistrict("岳麓区");
        dto.setReceiverDetailAddress("测试路一号");
        dto.setReceiverAddress("湖南省长沙市岳麓区测试路一号");
        dto.setPayType(payType);
        return dto;
    }

    private void configureWechat() {
        wechat.setEnabled(true);
        wechat.setMchId("12345678");
        wechat.setMerchantSerialNumber("merchant-serial");
        wechat.setPrivateKeyPath("/restricted/private.pem");
        wechat.setPublicKeyId("public-key-id");
        wechat.setPublicKeyPath("/restricted/public.pem");
        wechat.setApiV3Key("test-v3-key");
        wechat.setNotifyUrl("https://example.test/pay/notify");
        wechat.setRefundNotifyUrl("https://example.test/pay/refund-notify");
        miniProgram.setEnabled(true);
        miniProgram.setAppId("wx1234567890123456");
        miniProgram.setAppSecret("test-app-secret");
    }

    private void configureAlipay() {
        alipay.setAppId("app-id");
        alipay.setSellerId("seller-id");
        alipay.setPrivateKey("private-key");
        alipay.setAlipayPublicKey("public-key");
        alipay.setEnabled(true);
    }
}
