package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class AdminMemberPasswordResetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新登录密码，不是支付密码。 */
    @ToString.Exclude
    private String newPassword;

    private String reason;

    /** 当前后台管理员登录密码，仅用于本次二次验证。 */
    @ToString.Exclude
    private String adminPassword;
}
