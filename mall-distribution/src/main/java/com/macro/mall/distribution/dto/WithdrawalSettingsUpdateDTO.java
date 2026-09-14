package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class WithdrawalSettingsUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotNull private Boolean serviceEnabled;
    @Size(max = 120) private String disabledReason;
    @NotNull private Boolean balanceHolderEnabled;
    @NotNull private Boolean manualBalanceWithdrawable;
    @NotNull private Boolean bankCardEnabled;
    @NotNull private Boolean offlinePayoutEnabled;
    @NotNull private Long version;
    @NotBlank @Size(max = 200) private String changeReason;
}
