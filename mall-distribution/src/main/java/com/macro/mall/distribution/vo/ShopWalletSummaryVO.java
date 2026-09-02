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

    /** 支付密码锁定剩余秒数；未锁定时为 0。 */
    private Integer paymentPasswordLockRemainingSeconds;

    private Boolean distributionActivated;

    private Boolean realNameVerified;

    private Boolean adultVerified;

    private String maskedRealName;

    /** 单笔超过该金额时进入一次人工审核；首次提现和支付宝换号也会人工审核。 */
    private BigDecimal withdrawalManualReviewThreshold;
}
