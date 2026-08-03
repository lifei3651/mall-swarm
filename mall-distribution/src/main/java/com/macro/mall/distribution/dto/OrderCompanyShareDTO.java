package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCompanyShareDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private String accountName;

    private BigDecimal shareRate;

    private BigDecimal shareAmount;

    private String remark;
}
