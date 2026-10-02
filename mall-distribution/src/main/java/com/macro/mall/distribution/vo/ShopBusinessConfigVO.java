package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopBusinessConfigVO implements Serializable {

    private String businessMode;
    private com.macro.mall.distribution.service.CustomerBusinessModePolicy.Status agencyConfigStatus;
    private Boolean teamFeaturesEnabled;
    private Integer flashSaleEnabled;
    private Integer invitationEnabled;
    private String flashSaleBonusMode;
    private Integer repurchaseMallEnabled;
    private Integer couponEnabled;
    private Integer balanceTransactionsEnabled;
    private Integer multiMerchantEnabled;
    private String repurchaseEligibilityMode;
    private String repurchaseBonusMode;
    private Boolean repurchaseEligible;
    private String repurchaseEligibilityHint;
}
