package com.macro.mall.distribution.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Member-visible terms only; never return private funding configuration or other owners. */
@Data
public class ShopCouponVO {
    private Long id;
    private Long claimId;
    private String title;
    private String scopeLabel;
    private Long merchantId;
    private List<Long> productIds;
    private List<String> businessTypes;
    private BigDecimal amount;
    private BigDecimal minimumAmount;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String status;
    private boolean usable;
    private String reason;
    private String refundRule;
}
