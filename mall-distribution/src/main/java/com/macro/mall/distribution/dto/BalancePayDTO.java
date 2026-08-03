package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class BalancePayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private String paymentPassword;
}
