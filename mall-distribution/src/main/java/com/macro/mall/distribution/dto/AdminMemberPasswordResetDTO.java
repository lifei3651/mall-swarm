package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class AdminMemberPasswordResetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新登录密码，不是支付密码。 */
    @ToString.Exclude
    @NotBlank(message = "请输入新登录密码")
    @Size(min = 6, max = 32, message = "新登录密码需要6至32位")
    private String newPassword;

    @NotBlank(message = "请填写重置登录密码的原因")
    @Size(max = 300, message = "重置原因不能超过300个字")
    private String reason;

    /** 当前后台管理员登录密码，仅用于本次二次验证。 */
    @ToString.Exclude
    @NotBlank(message = "请输入当前管理员登录密码")
    @Size(min = 8, max = 64, message = "当前管理员登录密码长度不正确")
    private String adminPassword;
}
