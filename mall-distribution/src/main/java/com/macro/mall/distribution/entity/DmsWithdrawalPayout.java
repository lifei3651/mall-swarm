package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 会员奖金提现的渠道打款证据；一笔提现只保留当前受控尝试。 */
@Data
public class DmsWithdrawalPayout implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long withdrawId;
    private String withdrawNo;
    private Integer attemptNo;
    private String requestNo;
    private String channel;
    private String state;
    private String providerStatus;
    private String providerOrderNo;
    private BigDecimal amount;
    private String recipientHash;
    private String responseDigest;
    private String failureCode;

    @JsonIgnore
    private String confirmationPackage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
