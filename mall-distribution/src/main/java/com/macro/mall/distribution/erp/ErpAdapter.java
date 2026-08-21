package com.macro.mall.distribution.erp;

import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsShopOrder;

public interface ErpAdapter {
    String providerCode();
    ErpPushResult pushOrder(DmsErpIntegration integration, DmsShopOrder order);

    /** 厂商接口映射完成并通过联调后才允许管理员启用自动推单。 */
    default boolean orderPushReady() { return true; }

    record ErpPushResult(boolean success, String message) {
        public static ErpPushResult failed(String message) { return new ErpPushResult(false, message); }
    }
}
