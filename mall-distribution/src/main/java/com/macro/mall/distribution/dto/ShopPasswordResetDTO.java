package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ShopPasswordResetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @ToString.Exclude
    @NotBlank(message = "请输入短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码必须是6位数字")
    private String smsCode;

    @ToString.Exclude
    @NotBlank(message = "请输入新登录密码")
    @Size(min = 6, max = 32, message = "新登录密码需为6至32位")
    private String newPassword;
}
