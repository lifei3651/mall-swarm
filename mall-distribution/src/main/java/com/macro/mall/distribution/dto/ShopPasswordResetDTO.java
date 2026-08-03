package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ShopPasswordResetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String phone;

    @ToString.Exclude
    private String smsCode;

    @ToString.Exclude
    private String newPassword;
}
