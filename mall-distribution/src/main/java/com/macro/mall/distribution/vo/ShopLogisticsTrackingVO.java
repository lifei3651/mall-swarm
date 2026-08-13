package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopLogisticsTrackingVO {
    private Long shipmentId;
    private String deliveryCompany;
    private String deliveryNo;
    private boolean configured;
    private String providerCode;
    private String status;
    private String statusText;
    private LocalDateTime updatedAt;
    private List<Event> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private LocalDateTime eventTime;
        private String status;
        private String description;
        private String location;
    }
}
