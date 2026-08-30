package com.macro.mall.distribution.event;

/** 本地待支付订单已成功关闭后，通知微信支付侧关闭同一商户订单。 */
public record WeChatPayCloseEvent(String paymentNo) {
}
