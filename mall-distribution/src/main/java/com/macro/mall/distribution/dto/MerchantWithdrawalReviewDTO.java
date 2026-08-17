package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MerchantWithdrawalReviewDTO {
    @DecimalMin(value = "0", message = "应开票金额不能小于0")
    @Digits(integer = 12, fraction = 2, message = "应开票金额最多保留两位小数")
    private BigDecimal invoiceRequiredAmount;
    @DecimalMin(value = "0", message = "已收票金额不能小于0")
    @Digits(integer = 12, fraction = 2, message = "已收票金额最多保留两位小数")
    private BigDecimal invoiceReceivedAmount;
    private String invoiceStatus;
    @Digits(integer = 12, fraction = 2, message = "调整金额最多保留两位小数")
    private BigDecimal adjustmentAmount;
    @Size(max = 256, message = "调整原因不能超过256个字符")
    private String adjustmentReason;
}
