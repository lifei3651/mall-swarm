package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WechatExpressOrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$", message = "快递下单请求标识不正确")
    private String requestKey;
    @NotBlank @Size(max = 64) private String deliveryId;
    @NotBlank @Size(max = 128) private String bizId;
    @NotNull @Min(0) private Integer serviceType;
    @Positive private Integer shipmentQuantity;
    @NotNull @Min(1) @Max(20) private Integer packageCount;
    @NotNull @DecimalMin("0.01") @DecimalMax("1000") private BigDecimal weight;
    @NotNull @DecimalMin("0.1") @DecimalMax("500") private BigDecimal packageLength;
    @NotNull @DecimalMin("0.1") @DecimalMax("500") private BigDecimal packageWidth;
    @NotNull @DecimalMin("0.1") @DecimalMax("500") private BigDecimal packageHeight;
    @Size(max = 300) private String remark;
    private LocalDateTime expectedPickupTime;
}
