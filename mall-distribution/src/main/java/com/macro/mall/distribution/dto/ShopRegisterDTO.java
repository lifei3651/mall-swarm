package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ShopRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String phone;

    private String username;

    @ToString.Exclude
    private String password;

    private String nickname;

    private String inviteCode;

    /** 短信验证码 */
    @ToString.Exclude
    private String smsCode;
}
