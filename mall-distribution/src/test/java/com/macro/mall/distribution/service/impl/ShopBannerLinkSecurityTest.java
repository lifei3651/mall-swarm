package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsShopBanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShopBannerLinkSecurityTest {

    @Test
    void rejectsScriptAndNonTlsExternalLinks() {
        assertThrows(ApiException.class, () -> validate("URL", "javascript:alert(1)"));
        assertThrows(ApiException.class, () -> validate("URL", "http://example.com/banner"));
    }

    @Test
    void acceptsOnlyNormalizedHttpsExternalLinks() {
        DmsShopBanner banner = validate("url", " https://example.com/banner ");
        assertEquals("URL", banner.getLinkType());
        assertEquals("https://example.com/banner", banner.getLinkValue());
    }

    @Test
    void brandCultureUsesControlledActionAndNeverAcceptsAnInjectedUrl() {
        DmsShopBanner banner = validate("brand_culture", "https://attacker.example/redirect");
        assertEquals("BRAND_CULTURE", banner.getLinkType());
        assertNull(banner.getLinkValue());
        assertThrows(ApiException.class, () -> validate("/brand-culture", "javascript:alert(1)"));
    }

    private DmsShopBanner validate(String type, String value) {
        DmsShopBanner banner = new DmsShopBanner();
        banner.setLinkType(type);
        banner.setLinkValue(value);
        ShopServiceImpl.validateBannerLink(banner);
        return banner;
    }
}
