package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BalanceTransferDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入收款人手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位收款人手机号")
    private String recipientPhone;

    @NotNull(message = "请输入转账金额")
    @DecimalMin(value = "1", message = "转账金额必须为正整数")
    @Digits(integer = 12, fraction = 0, message = "转账金额只能为整数")
    private BigDecimal amount;

    @NotBlank(message = "请输入支付密码")
    @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字")
    @ToString.Exclude
    private String paymentPassword;

    @Size(max = 100, message = "转账备注不能超过100个字")
    private String remark;
}
