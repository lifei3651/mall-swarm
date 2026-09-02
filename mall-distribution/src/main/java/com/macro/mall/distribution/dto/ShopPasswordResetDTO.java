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

    /** 图形验证码编号；与短信验证码在最终重置提交时统一校验。 */
    @ToString.Exclude
    @NotBlank(message = "请输入图形验证码")
    @Size(max = 64, message = "图形验证码无效，请刷新后重试")
    private String captchaId;

    /** 图形验证码；不再作为获取短信验证码的前置条件。 */
    @ToString.Exclude
    @NotBlank(message = "请输入图形验证码")
    @Pattern(regexp = "^[A-Za-z0-9]{4}$", message = "请输入4位图形验证码")
    private String captchaCode;

    @ToString.Exclude
    @NotBlank(message = "请输入新登录密码")
    @Size(min = 10, max = 32, message = "新登录密码需为10至32位")
    private String newPassword;
}
