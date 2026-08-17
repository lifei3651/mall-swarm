package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MerchantWithdrawalApplyDTO {
    @NotNull(message = "请选择商户")
    private Long merchantId;
    @NotNull(message = "请输入申请金额")
    @DecimalMin(value = "0.01", message = "申请金额必须大于0")
    @Digits(integer = 12, fraction = 2, message = "申请金额最多保留两位小数")
    private BigDecimal requestedAmount;
}
