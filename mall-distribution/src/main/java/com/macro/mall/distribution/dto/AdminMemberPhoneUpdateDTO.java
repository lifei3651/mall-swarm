package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class AdminMemberPhoneUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入新手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @NotBlank(message = "请填写修改手机号的原因")
    @Size(max = 300, message = "修改原因不能超过300个字")
    private String reason;

    /** 当前后台管理员登录密码，仅用于本次二次验证。 */
    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String adminPassword;
}
