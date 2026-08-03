package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsOrderCompanyShare implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long orderId;

    private String orderNo;

    private Long accountId;

    private String accountName;

    private BigDecimal shareRate;

    private BigDecimal shareAmount;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
