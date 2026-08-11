package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

@Data
public class ShopPhoneUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    @NotBlank(message = "请输入原手机号短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "原手机号短信验证码必须是6位数字")
    private String currentPhoneSmsCode;

    @NotBlank(message = "请输入新手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位新手机号")
    private String newPhone;

    @ToString.Exclude
    @NotBlank(message = "请输入新手机号短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "新手机号短信验证码必须是6位数字")
    private String newPhoneSmsCode;
}
