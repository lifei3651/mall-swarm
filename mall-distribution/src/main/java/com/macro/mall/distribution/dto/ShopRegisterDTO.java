package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ShopRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @NotBlank(message = "请输入登录账号")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,19}$", message = "登录账号需为4至20位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;

    @NotBlank(message = "请输入登录密码")
    @Size(min = 6, max = 32, message = "登录密码需为6至32位")
    @ToString.Exclude
    private String password;

    private String nickname;

    private String inviteCode;

    /** 短信验证码 */
    @NotBlank(message = "请输入短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码必须是6位数字")
    @ToString.Exclude
    private String smsCode;
}
