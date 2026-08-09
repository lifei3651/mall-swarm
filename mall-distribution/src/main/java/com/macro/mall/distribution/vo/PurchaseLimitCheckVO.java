package com.macro.mall.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 加入购物车前的会员限购校验结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLimitCheckVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean allowed;
    private Integer purchaseLimit;
    private Integer purchasedQuantity;
    private Integer remainingQuantity;
    private String productName;
    private String message;
}
