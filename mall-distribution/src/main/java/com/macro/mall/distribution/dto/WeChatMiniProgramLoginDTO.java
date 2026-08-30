package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class WeChatMiniProgramLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "缺少微信登录凭证")
    @Size(max = 256, message = "微信登录凭证格式不正确")
    @ToString.Exclude
    private String loginCode;

    /** 新账号或首次绑定时由 getPhoneNumber 返回；与 wx.login 的 code 不能混用。 */
    @Size(max = 256, message = "微信手机号凭证格式不正确")
    @ToString.Exclude
    private String phoneCode;

    @Pattern(regexp = "(?i)^[A-Z0-9]{8}$", message = "邀请码格式不正确")
    private String inviteCode;

    @AssertTrue(message = "请先阅读并同意隐私政策")
    private boolean privacyAgreed;

    @NotBlank(message = "缺少隐私授权版本")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,64}$", message = "隐私授权版本格式不正确")
    private String privacyConsentVersion;
}
