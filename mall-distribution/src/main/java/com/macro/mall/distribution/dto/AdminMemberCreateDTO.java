package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/** 后台创建会员；登录账号和手机号必填，密码可选。 */
@Data
public class AdminMemberCreateDTO implements Serializable {
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;
    @NotBlank(message = "请输入登录账号")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,19}$", message = "登录账号需为4至20位，必须以英文字母开头且仅支持字母、数字和下划线")
    private String username;
    @ToString.Exclude
    @Size(min = 10, max = 32, message = "登录密码需为10至32位")
    private String password;
    @Size(max = 20, message = "昵称不能超过20个字符")
    private String nickname;
    private Long inviterUserId;
    private Boolean activateDistribution;
    private Integer initialLevel;
    @Size(max = 300, message = "创建原因不能超过300个字")
    private String reason;
}
