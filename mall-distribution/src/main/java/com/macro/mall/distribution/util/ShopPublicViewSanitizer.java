package com.macro.mall.distribution.util;

import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.vo.ShopOrderVO;

/** 公共商城响应的集中净化边界，避免直接序列化后台经营字段。 */
public final class ShopPublicViewSanitizer {
    private ShopPublicViewSanitizer() {
    }

    public static DmsShopProduct product(DmsShopProduct value, boolean repurchaseView) {
        if (value == null) return null;
        value.setCostAmount(null);
        value.setBvValue(null);
        value.setSafetyStock(null);
        value.setShippingAddressId(null);
        value.setReturnAddressId(null);
        value.setFreightTemplateId(null);
        value.setCreateTime(null);
        value.setUpdateTime(null);
        if (!repurchaseView) {
            value.setRepurchaseSaleEnabled(null);
            value.setRepurchasePrice(null);
            value.setRepurchasePv(null);
            value.setRepurchasePurchaseLimit(null);
        }
        return value;
    }

    public static DmsShopSku sku(DmsShopSku value, boolean repurchaseView) {
        if (value == null) return null;
        value.setCostAmount(null);
        value.setBvValue(null);
        value.setSafetyStock(null);
        value.setCreateTime(null);
        value.setUpdateTime(null);
        if (!repurchaseView) {
            value.setRepurchasePrice(null);
            value.setRepurchasePv(null);
        }
        return value;
    }

    public static ShopOrderVO order(ShopOrderVO value) {
        if (value == null) return null;
        value.setFinance(null);
        if (value.getOrder() != null) value.getOrder().setTotalCost(null);
        if (value.getItems() != null) value.getItems().forEach(item -> {
            item.setCostAmount(null);
            item.setTotalCost(null);
        });
        return value;
    }
}
