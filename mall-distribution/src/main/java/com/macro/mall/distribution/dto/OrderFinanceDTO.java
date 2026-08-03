package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderFinanceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String orderNo;

    private BigDecimal payAmount;

    private BigDecimal productCost;

    private String remark;
}
