package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WithdrawalPayoutVO {
    private Long withdrawId;
    private String requestNo;
    private String channel;
    private String state;
    private String providerStatus;
    private String providerOrderNo;
    private BigDecimal amount;
    private String failureCode;
    private boolean userConfirmationRequired;
    private LocalDateTime updateTime;
}
