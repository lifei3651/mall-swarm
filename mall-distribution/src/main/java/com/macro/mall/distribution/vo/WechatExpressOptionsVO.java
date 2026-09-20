package com.macro.mall.distribution.vo;

import java.util.List;

public record WechatExpressOptionsVO(boolean configured, String message, List<Account> accounts) {
    public record Account(String bizId, String deliveryId, String deliveryName, String alias,
                          int statusCode, long quota, List<ServiceType> serviceTypes) { }
    public record ServiceType(int serviceType, String serviceName) { }
}
