package com.macro.mall.distribution.erp;

import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsShopOrder;
import org.springframework.stereotype.Component;

@Component
public class KingdeeErpAdapter extends AbstractCredentialErpAdapter {
    @Override public String providerCode() { return "KINGDEE"; }
    @Override public ErpPushResult pushOrder(DmsErpIntegration integration, DmsShopOrder order) { return checkConfiguration(integration); }
}
