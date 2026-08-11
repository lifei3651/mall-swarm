package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class BalancePayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入支付密码")
    @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字")
    @ToString.Exclude
    private String paymentPassword;
}
