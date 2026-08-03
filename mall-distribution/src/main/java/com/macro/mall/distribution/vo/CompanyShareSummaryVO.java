package com.macro.mall.distribution.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompanyShareSummaryVO {

    private Long accountId;

    private String accountName;

    private Long orderCount;

    private BigDecimal shareAmount;
}
