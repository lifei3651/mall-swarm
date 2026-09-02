package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class AdminTemporaryCredentialDTO {
    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String currentAdminPassword;
}
