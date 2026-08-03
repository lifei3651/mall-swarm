package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class BalanceTransferDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipientPhone;

    private BigDecimal amount;

    @ToString.Exclude
    private String paymentPassword;

    private String remark;
}
