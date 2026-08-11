package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class BalanceRecipientQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入收款人手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位收款人手机号")
    private String phone;
}
