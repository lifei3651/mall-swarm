package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DashboardCommissionVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String agentName;
    private String orderNo;
    private String bonusType;
    private BigDecimal commissionAmount;
    private Integer status;
    private LocalDateTime createTime;
}
