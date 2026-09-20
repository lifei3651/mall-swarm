package com.macro.mall.distribution.vo;

public record WechatExpressShipmentVO(Long expressOrderId, Long shipmentId, String deliveryCompany,
                                      String deliveryNo, String status, String printHtmlBase64) { }
