package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantDepositAdjustDTO {
    @NotNull(message = "请选择商户")
    private Long merchantId;
    @NotBlank(message = "缺少操作请求号")
    @Size(max = 64, message = "操作请求号不能超过64个字符")
    private String operationNo;
    @NotNull(message = "保证金金额不能为空")
    @DecimalMin(value = "0.01", message = "保证金金额必须大于0")
    private BigDecimal amount;
    @NotBlank(message = "请填写保证金调整原因")
    @Size(max = 256, message = "保证金调整原因不能超过256个字符")
    private String reason;
}
