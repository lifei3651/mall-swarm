package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ShopWithdrawalApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal withdrawAmount;
    private Integer withdrawType;
    private String bankName;
    private String bankAccount;
    private String accountName;
    @ToString.Exclude
    private String paymentPassword;
    @ToString.Exclude
    private String smsCode;
}
