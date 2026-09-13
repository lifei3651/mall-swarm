package com.macro.mall.distribution.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DmsShopCouponClaim {
    private Long id;
    private Long tenantId;
    private Long couponId;
    private Long memberId;
    private Long userId;
    private String requestId;
    private String status;
    private Long orderId;
    private LocalDateTime createTime;
}
