package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class AdminLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请输入后台账号")
    @Size(max = 64, message = "后台账号不能超过64个字符")
    private String username;

    @ToString.Exclude
    @NotBlank(message = "请输入后台密码")
    @Size(max = 64, message = "后台密码不能超过64位")
    private String password;

    @Size(max = 128, message = "图形验证码标识过长")
    private String captchaId;

    @ToString.Exclude
    @Size(max = 16, message = "图形验证码不能超过16个字符")
    private String captchaCode;
}
