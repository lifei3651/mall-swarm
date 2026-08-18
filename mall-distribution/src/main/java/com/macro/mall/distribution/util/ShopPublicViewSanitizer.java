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
        value.setSettlementDelayDaysOverride(null);
        value.setBvValue(null);
        value.setSafetyStock(null);
        value.setDeliveryAddress(null);
        value.setDeliveryProvince(null);
        value.setDeliveryCity(null);
        value.setDeliveryDistrict(null);
        value.setShippingAddressId(null);
        value.setReturnAddressId(null);
        value.setFreightTemplateId(null);
        value.setMerchantId(null);
        value.setEnrollmentSaleEnabled(null);
        value.setTeamBonusMode(null);
        value.setMerchantReviewStatus(null);
        value.setMerchantReviewVersion(null);
        value.setMerchantReviewRemark(null);
        value.setMerchantReviewSubmittedAt(null);
        value.setMerchantReviewedAt(null);
        value.setMerchantReviewerId(null);
        value.setMerchantReviewerName(null);
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
        if (value.getOrder() != null) {
            value.getOrder().setTotalCost(null);
            value.getOrder().setMerchantId(null);
        }
        if (value.getItems() != null) value.getItems().forEach(item -> {
            item.setCostAmount(null);
            item.setTotalCost(null);
            item.setSettlementDelayDays(null);
            item.setMerchantId(null);
            item.setTeamBonusMode(null);
        });
        return value;
    }
}
