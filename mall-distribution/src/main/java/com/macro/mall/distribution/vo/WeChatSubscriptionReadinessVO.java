package com.macro.mall.distribution.vo;

/** 配置检查，不等同于微信真实送达；不包含客户密钥或用户信息。 */
public record WeChatSubscriptionReadinessVO(String eventType, String title, boolean templateConfigured,
                                           boolean runtimeReady, String detail) { }
