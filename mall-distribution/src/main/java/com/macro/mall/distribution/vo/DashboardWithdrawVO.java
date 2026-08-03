package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DashboardWithdrawVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String withdrawNo;
    private String agentName;
    private String accountName;
    private BigDecimal withdrawAmount;
    private LocalDateTime createTime;
}
