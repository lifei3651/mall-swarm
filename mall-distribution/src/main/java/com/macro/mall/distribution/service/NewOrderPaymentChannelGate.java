package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.AlipayConfig;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Only gates new order creation. Existing orders must still accept verified payment callbacks
 * and refunds after a channel is disabled, otherwise an external charge can lose its local record.
 */
@Service
public class NewOrderPaymentChannelGate {
    private final AlipayConfig alipayConfig;
    private final WeChatPayProperties weChatPayProperties;
    private final WeChatMiniProgramProperties miniProgramProperties;
    private final boolean simulationPaymentEnabled;

    public NewOrderPaymentChannelGate(AlipayConfig alipayConfig,
                                      WeChatPayProperties weChatPayProperties,
                                      WeChatMiniProgramProperties miniProgramProperties,
                                      @Value("${shop.payment.simulation-enabled:false}") boolean simulationPaymentEnabled) {
        this.alipayConfig = alipayConfig;
        this.weChatPayProperties = weChatPayProperties;
        this.miniProgramProperties = miniProgramProperties;
        this.simulationPaymentEnabled = simulationPaymentEnabled;
    }

    public void requireAvailable(String payType) {
        if ("BALANCE".equals(payType)) return; // The tenant balance-mode gate owns this channel.
        if ("ALIPAY".equals(payType)) {
            // The simulation flag is restricted to explicit local/test profiles by ProductionSafetyGuard.
            if (!simulationPaymentEnabled && !alipayConfig.isConfigured()) {
                Asserts.fail("支付宝支付当前不可用，请选择其他支付方式");
            }
            return;
        }
        if ("WECHAT".equals(payType)) {
            if (!simulationPaymentEnabled && (!weChatPayProperties.isConfigured() || !miniProgramProperties.loginReady())) {
                Asserts.fail("微信支付当前不可用，请选择其他支付方式");
            }
            return;
        }
        Asserts.fail("支付方式不正确");
    }
}
