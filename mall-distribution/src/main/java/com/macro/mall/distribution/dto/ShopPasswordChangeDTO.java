package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ShopPasswordChangeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    @NotBlank(message = "请输入当前登录密码")
    @Size(max = 32, message = "当前登录密码不能超过32位")
    private String currentPassword;

    @ToString.Exclude
    @NotBlank(message = "请输入新登录密码")
    @Size(min = 6, max = 32, message = "新登录密码需为6至32位")
    private String newPassword;

    @ToString.Exclude
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码必须是6位数字")
    private String smsCode;
}
