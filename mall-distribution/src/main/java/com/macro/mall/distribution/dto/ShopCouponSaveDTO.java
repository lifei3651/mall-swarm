package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShopCouponSaveDTO {
    @NotBlank @Size(max=60) private String title;
    private Long merchantId;
    @NotNull @Pattern(regexp="ALL|PRODUCTS") private String scopeType;
    @Size(max=200) private List<@NotNull @Positive Long> productIds;
    @NotEmpty @Size(max=2) private List<@Pattern(regexp="NORMAL|REPURCHASE") String> businessTypes;
    @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) private BigDecimal amount;
    @NotNull @DecimalMin("0") @DecimalMax("999999.99") @Digits(integer=6,fraction=2) private BigDecimal minimumAmount;
    @NotNull @Min(0) @Max(100) private Integer merchantPercent;
    @NotNull @Pattern(regexp="GROSS|NET") private String bonusBasis;
    @NotNull @Pattern(regexp="FULL_RETURN|NEVER") private String refundRule;
    @NotNull private LocalDateTime startsAt;
    @NotNull private LocalDateTime endsAt;
    @NotNull @Min(1) @Max(1000000) private Integer totalCount;
    @NotNull @Min(1) @Max(10) private Integer perMemberLimit;
    @NotNull @Min(0) private Integer version;
    @NotNull @AssertTrue(message="请核对并确认优惠券对结算和奖金的影响") private Boolean impactConfirmed;
}
