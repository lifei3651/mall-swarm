package com.macro.mall.distribution.constants;

import java.util.Set;

/** 商城订单业务类型；各类型共用履约与售后底座，但价格、资格和奖金策略相互隔离。 */
public final class ShopBusinessType {

    public static final String NORMAL = "NORMAL";
    public static final String FLASH_SALE = "FLASH_SALE";
    public static final String REPURCHASE = "REPURCHASE";
    public static final Set<String> ALL = Set.of(NORMAL, FLASH_SALE, REPURCHASE);

    private ShopBusinessType() {
    }
}
