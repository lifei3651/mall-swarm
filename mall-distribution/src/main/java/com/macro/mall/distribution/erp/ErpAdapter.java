package com.macro.mall.distribution.erp;

import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsShopOrder;

public interface ErpAdapter {
    String providerCode();
    ErpPushResult pushOrder(DmsErpIntegration integration, DmsShopOrder order);

    record ErpPushResult(boolean success, String message) {
        public static ErpPushResult failed(String message) { return new ErpPushResult(false, message); }
    }
}
