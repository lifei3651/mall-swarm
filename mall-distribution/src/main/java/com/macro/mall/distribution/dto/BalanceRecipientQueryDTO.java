package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class BalanceRecipientQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String phone;
}
