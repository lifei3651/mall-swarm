package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.TenantLegalTemplatesVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantLegalTemplateSupportTest {

    private final TenantLegalTemplateSupport support = new TenantLegalTemplateSupport();

    @Test
    void fillsBlankLegalContentWithReusableTenantPlaceholders() {
        DmsTenant tenant = new DmsTenant();

        assertTrue(support.applyDefaults(tenant));
        assertEquals(TenantLegalTemplateSupport.DEFAULT_SERVICE_HOURS, tenant.getServiceHours());
        assertTrue(tenant.getUserAgreement().contains("{{companyName}}"));
        assertTrue(tenant.getPrivacyPolicy().contains("{{thirdPartyServices}}"));
        assertTrue(tenant.getAfterSalePolicy().contains("{{servicePhone}}"));
    }

    @Test
    void neverOverwritesCustomerEditedLegalContent() {
        DmsTenant tenant = new DmsTenant();
        tenant.setUserAgreement("客户确认的协议");
        tenant.setPrivacyPolicy("客户确认的隐私政策");
        tenant.setAfterSalePolicy("客户确认的售后规则");
        tenant.setServiceHours("工作日 10:00-18:00");
        tenant.setThirdPartyServices("客户实际服务商");

        assertFalse(support.applyDefaults(tenant));
        assertEquals("客户确认的协议", tenant.getUserAgreement());
        assertEquals("客户确认的隐私政策", tenant.getPrivacyPolicy());
        assertEquals("客户确认的售后规则", tenant.getAfterSalePolicy());
    }

    @Test
    void templatesDoNotFreezeOneCustomersBonusPercentages() {
        TenantLegalTemplatesVO templates = support.templates();
        String all = templates.userAgreement() + templates.privacyPolicy() + templates.afterSalePolicy();

        assertFalse(all.contains("65%"));
        assertTrue(all.contains("客户当期方案"));
    }
}
