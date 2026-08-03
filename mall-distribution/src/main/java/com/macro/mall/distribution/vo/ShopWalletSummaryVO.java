package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ShopWalletSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal balance;

    private Boolean hasPaymentPassword;

    private Boolean paymentPasswordLocked;

    private Boolean distributionActivated;
}
