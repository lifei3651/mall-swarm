package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MerchantWithdrawalPayDTO {
    @NotNull(message = "请输入实际打款金额")
    @DecimalMin(value = "0.01", message = "实际打款金额必须大于0")
    @Digits(integer = 12, fraction = 2, message = "实际打款金额最多保留两位小数")
    private BigDecimal actualPaidAmount;
    @Size(max = 128, message = "打款流水号不能超过128个字符")
    private String paymentReference;
    @Size(max = 512, message = "打款凭证地址不能超过512个字符")
    private String paymentVoucherUrl;
}
