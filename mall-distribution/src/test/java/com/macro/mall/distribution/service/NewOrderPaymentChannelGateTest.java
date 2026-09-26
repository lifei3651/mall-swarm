package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewOrderPaymentChannelGateTest {
    private final AlipayConfig alipay = new AlipayConfig();
    private final WeChatPayProperties wechat = new WeChatPayProperties();
    private final WeChatMiniProgramProperties miniProgram = new WeChatMiniProgramProperties();

    @Test
    void configuredWechatDoesNotEnableUnconfiguredAlipay() {
        configureWechat();
        NewOrderPaymentChannelGate gate = new NewOrderPaymentChannelGate(alipay, wechat, miniProgram, false);

        assertDoesNotThrow(() -> gate.requireAvailable("WECHAT"));
        assertThrows(ApiException.class, () -> gate.requireAvailable("ALIPAY"));
        miniProgram.setEnabled(false);
        assertThrows(ApiException.class, () -> gate.requireAvailable("WECHAT"));
    }

    @Test
    void bothClosedOrIncompleteChannelsRejectNewOrders() {
        NewOrderPaymentChannelGate gate = new NewOrderPaymentChannelGate(alipay, wechat, miniProgram, false);
        assertThrows(ApiException.class, () -> gate.requireAvailable("WECHAT"));
        assertThrows(ApiException.class, () -> gate.requireAvailable("ALIPAY"));
        assertDoesNotThrow(() -> gate.requireAvailable("BALANCE"));

        alipay.setEnabled(true);
        alipay.setAppId("app-id");
        assertThrows(ApiException.class, () -> gate.requireAvailable("ALIPAY"));
        alipay.setSellerId("seller-id");
        alipay.setPrivateKey("private-key");
        alipay.setAlipayPublicKey("public-key");
        assertDoesNotThrow(() -> gate.requireAvailable("ALIPAY"));
    }

    @Test
    void explicitTestSimulationAllowsUnconfiguredExternalChannels() {
        NewOrderPaymentChannelGate gate = new NewOrderPaymentChannelGate(alipay, wechat, miniProgram, true);
        assertDoesNotThrow(() -> gate.requireAvailable("WECHAT"));
        assertDoesNotThrow(() -> gate.requireAvailable("ALIPAY"));
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
}
