package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class AdminPasswordDTO {

    @ToString.Exclude
    @NotBlank(message = "密码不能为空")
    @Size(min = 10, max = 64, message = "后台密码需要10至64位")
    private String password;

    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String currentAdminPassword;
}
