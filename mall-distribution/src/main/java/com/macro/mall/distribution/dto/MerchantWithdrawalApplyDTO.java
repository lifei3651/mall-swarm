package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MerchantWithdrawalApplyDTO {
    @NotBlank(message = "缺少提现申请请求号")
    @Size(max = 64, message = "提现申请请求号不能超过64个字符")
    private String requestNo;
    @NotNull(message = "请选择商户")
    private Long merchantId;
    @NotNull(message = "请输入申请金额")
    @DecimalMin(value = "0.01", message = "申请金额必须大于0")
    @Digits(integer = 12, fraction = 2, message = "申请金额最多保留两位小数")
    private BigDecimal requestedAmount;
}
