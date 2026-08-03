package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class AdminLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    @ToString.Exclude
    private String password;

    private String captchaId;

    @ToString.Exclude
    private String captchaCode;
}
