package com.macro.mall.distribution.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Once published, financial terms are immutable; a new offer requires a new template. */
@Data
public class DmsShopCoupon {
    private Long id;
    private Long tenantId;
    private String title;
    private Long merchantId;
    private String merchantName;
    private String scopeType;
    private String productIdsJson;
    private String businessTypesJson;
    private BigDecimal amount;
    private BigDecimal minimumAmount;
    private Integer merchantPercent;
    private String bonusBasis;
    private String refundRule;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Integer totalCount;
    private Integer perMemberLimit;
    private Integer issuedCount;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
}
