package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ShopLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String account;

    @ToString.Exclude
    private String password;

    /** 短信验证码（loginType=sms 时使用） */
    @ToString.Exclude
    private String smsCode;

    /** 登录方式：password | sms */
    private String loginType;

    /** 密码登录的图形验证码 */
    private String captchaId;

    @ToString.Exclude
    private String captchaCode;
}
