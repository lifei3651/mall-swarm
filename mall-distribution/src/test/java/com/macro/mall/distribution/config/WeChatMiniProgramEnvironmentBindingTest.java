package com.macro.mall.distribution.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeChatMiniProgramEnvironmentBindingTest {
    @Test
    void directEnvironmentBindsWithoutPackagedApplicationYamlAndLeavesOtherCapabilitiesOff() {
        var source = new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                "SHOP_WECHATMINIPROGRAM_ENABLED", "true",
                "SHOP_WECHATMINIPROGRAM_PHONEAUTHORIZATIONENABLED", "true",
                "SHOP_WECHATMINIPROGRAM_APPID", "wx-test-app",
                "SHOP_WECHATMINIPROGRAM_APPSECRET", "test-only-secret",
                "SHOP_WECHATMINIPROGRAM_PRIVACYCONSENTVERSION", "MINI_PROGRAM_PRIVACY_V1",
                "SHOP_WECHATMINIPROGRAM_MINIPROGRAMSTATE", "formal",
                "SHOP_WECHATMINIPROGRAM_SUBSCRIBEMESSAGEENABLED", "false",
                "SHOP_WECHATMINIPROGRAM_SHIPPINGINFOENABLED", "false"));
        var binder = new Binder(ConfigurationPropertySources.from(source));
        var properties = binder.bind("shop.wechat-mini-program", Bindable.of(WeChatMiniProgramProperties.class)).get();
        assertTrue(properties.loginReady());
        assertTrue(properties.phoneAuthorizationReady());
        assertEquals("wx-test-app", properties.getAppId());
        assertEquals("test-only-secret", properties.getAppSecret());
        assertEquals("MINI_PROGRAM_PRIVACY_V1", properties.getPrivacyConsentVersion());
        assertEquals("formal", properties.safeMiniProgramState());
        assertFalse(properties.subscribeMessageReady());
        assertFalse(properties.shippingInfoReady());
        assertFalse(binder.bind("shop.wechat-pay", Bindable.of(WeChatPayProperties.class)).isBound());
    }
}
