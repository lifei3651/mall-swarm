package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class SmsCodeRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String phone;
    private Integer bizType;

    @ToString.Exclude
    private String code;
}
