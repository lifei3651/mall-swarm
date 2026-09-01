package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class WithdrawConfirmPayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入打款流水号")
    @Size(max = 128, message = "打款流水号不能超过128个字符")
    private String payNo;
}
