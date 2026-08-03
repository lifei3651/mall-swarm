package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ShopPasswordChangeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private String currentPassword;

    @ToString.Exclude
    private String newPassword;

    @ToString.Exclude
    private String smsCode;
}
