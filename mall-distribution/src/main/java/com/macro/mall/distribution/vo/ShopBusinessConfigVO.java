package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopBusinessConfigVO implements Serializable {

    private Integer flashSaleEnabled;
    private String flashSaleBonusMode;
    private Integer repurchaseMallEnabled;
    private String repurchaseEligibilityMode;
    private String repurchaseBonusMode;
    private Boolean repurchaseEligible;
    private String repurchaseEligibilityHint;
}
