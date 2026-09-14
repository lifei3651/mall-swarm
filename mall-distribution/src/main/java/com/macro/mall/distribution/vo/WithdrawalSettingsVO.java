package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class WithdrawalSettingsVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean serviceEnabled;
    private String disabledReason;
    private Boolean balanceHolderEnabled;
    private Boolean manualBalanceWithdrawable;
    private Boolean bankCardEnabled;
    private Boolean offlinePayoutEnabled;
    private Long version;
}
