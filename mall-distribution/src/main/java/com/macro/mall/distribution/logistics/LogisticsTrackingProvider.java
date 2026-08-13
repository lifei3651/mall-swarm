package com.macro.mall.distribution.logistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户物流查询适配器。接入快递鸟、快递100或承运商直连接口时只实现本接口，
 * 订单控制器和会员端不绑定具体供应商。
 */
public interface LogisticsTrackingProvider {
    String providerCode();

    default boolean supports(String deliveryCompany) {
        return true;
    }

    TrackingResult query(String deliveryCompany, String deliveryNo);

    record TrackingResult(String status, String statusText, LocalDateTime updatedAt, List<Event> events) { }

    record Event(LocalDateTime eventTime, String status, String description, String location) { }
}
