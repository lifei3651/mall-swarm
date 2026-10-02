package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** 客户规则草稿，保存不等于启用，也不执行门槛或奖励计算。 */
@Data
public class AgencyRuleDraft {
    @Pattern(regexp="SINGLE_ORDER|CUMULATIVE|SPECIFIED_PRODUCTS")
    private String thresholdType;
    @DecimalMin(value="0", inclusive=false) @Digits(integer=10, fraction=2)
    private BigDecimal thresholdAmount;
    @Size(max=100)
    private List<@Positive Long> productIds;
    @Pattern(regexp="AUTOMATIC|MANUAL_REVIEW")
    private String openingMethod;
    @Min(1) @Max(8)
    private Integer targetLevel;
    @Size(max=1000)
    private String attributionRule;
    @Size(max=1000)
    private String refundRule;
    @Size(max=1000)
    private String rewardPolicy;
}
