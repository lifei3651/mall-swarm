package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCompanyShareVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long accountId;

    private String accountName;

    private BigDecimal shareRate;

    private BigDecimal shareAmount;

    private String remark;
}
