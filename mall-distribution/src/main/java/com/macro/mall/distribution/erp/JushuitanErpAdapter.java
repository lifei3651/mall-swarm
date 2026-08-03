package com.macro.mall.distribution.erp;

import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsShopOrder;
import org.springframework.stereotype.Component;

@Component
public class JushuitanErpAdapter extends AbstractCredentialErpAdapter {
    @Override public String providerCode() { return "JUSHUITAN"; }
    @Override public ErpPushResult pushOrder(DmsErpIntegration integration, DmsShopOrder order) { return checkConfiguration(integration); }
}
