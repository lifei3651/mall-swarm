package com.macro.mall.distribution.erp;

import com.macro.mall.distribution.entity.DmsErpIntegration;

import java.net.URI;

abstract class AbstractCredentialErpAdapter implements ErpAdapter {
    @Override
    public boolean orderPushReady() {
        return false;
    }

    protected ErpPushResult checkConfiguration(DmsErpIntegration integration) {
        if (integration.getEndpoint() == null || integration.getEndpoint().isBlank()
                || integration.getAppKey() == null || integration.getAppKey().isBlank()
                || integration.getAppSecret() == null || integration.getAppSecret().isBlank()) {
            return ErpPushResult.failed("ERP 尚未完成接口地址或开发者凭据配置");
        }
        URI endpoint;
        try {
            endpoint = URI.create(integration.getEndpoint());
        } catch (IllegalArgumentException ex) {
            return ErpPushResult.failed("ERP 接口地址格式不正确");
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null) {
            return ErpPushResult.failed("ERP 接口必须使用 HTTPS");
        }
        return ErpPushResult.failed(providerCode() + " 尚未根据该商户授权文档完成接口映射");
    }
}
