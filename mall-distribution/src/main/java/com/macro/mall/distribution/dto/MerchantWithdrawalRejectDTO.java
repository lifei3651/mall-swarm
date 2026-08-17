package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantWithdrawalRejectDTO {
    @NotBlank(message = "请填写驳回原因")
    @Size(max = 256, message = "驳回原因不能超过256个字符")
    private String reason;
}
