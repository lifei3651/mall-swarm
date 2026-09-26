package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

/** 商品管理使用的商户选项，不暴露商户的身份、收款和开票资料。 */
@Data
public class MerchantOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String merchantName;
    private Integer status;
    private Integer defaultSettlementDays;
}
