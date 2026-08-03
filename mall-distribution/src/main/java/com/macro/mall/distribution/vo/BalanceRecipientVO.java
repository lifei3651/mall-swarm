package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class BalanceRecipientVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String phone;

    private String memberName;
}
