package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class AdminSelfPasswordDTO {

    @ToString.Exclude
    @NotBlank(message = "请输入当前后台密码")
    @Size(min = 8, max = 64, message = "当前后台密码长度不正确")
    private String currentPassword;

    @ToString.Exclude
    @NotBlank(message = "请输入新后台密码")
    @Size(min = 10, max = 64, message = "新后台密码需要10至64位")
    private String newPassword;
}
