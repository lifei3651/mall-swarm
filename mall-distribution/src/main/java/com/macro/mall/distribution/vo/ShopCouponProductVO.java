package com.macro.mall.distribution.vo;

import lombok.Data;
import java.math.BigDecimal;

/** Minimal public coupon product projection: never expose costs or merchant credentials. */
@Data
public class ShopCouponProductVO {
    private Long id;
    private String productName;
    private String coverUrl;
    private BigDecimal salePrice;
}
