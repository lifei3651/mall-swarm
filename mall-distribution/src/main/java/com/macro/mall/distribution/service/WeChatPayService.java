package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.WeChatPayParametersVO;
import com.macro.mall.distribution.wechat.WeChatPayGateway;

import java.math.BigDecimal;

public interface WeChatPayService {

    boolean isConfigured();

    WeChatPayParametersVO createPayOrder(Long checkoutOrOrderId, DmsShopMember member);

    boolean reconcileOrder(Long checkoutOrOrderId, DmsShopMember member);

    /** 本地订单提交关闭后调用；该方法自行处理微信已支付竞态，不向取消事务反抛渠道异常。 */
    void closeOrder(String paymentNo);

    void handlePaymentNotification(WeChatPayGateway.NotificationRequest request);

    RefundState requestRefund(String paymentNo, String refundNo, BigDecimal refundAmount,
                              BigDecimal paymentAmount, String reason);

    WeChatPayGateway.RefundNotification parseRefundNotification(WeChatPayGateway.NotificationRequest request);

    enum RefundState {
        COMPLETED,
        PROCESSING,
        FAILED
    }
}
