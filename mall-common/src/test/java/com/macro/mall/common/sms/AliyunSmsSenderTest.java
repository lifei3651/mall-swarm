package com.macro.mall.common.sms;

import com.aliyun.teaopenapi.models.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AliyunSmsSenderTest {

    @Test
    void sdkClientUsesConfiguredBoundedTimeouts() {
        AliyunSmsProperties properties = new AliyunSmsProperties();
        properties.setAccessKeyId("test-id");
        properties.setAccessKeySecret("test-secret");
        properties.setEndpoint("dysmsapi.aliyuncs.com");
        properties.setConnectTimeoutMs(7000);
        properties.setReadTimeoutMs(12000);

        Config config = new AliyunSmsSender(properties).buildClientConfig();

        assertEquals(7000, config.getConnectTimeout());
        assertEquals(12000, config.getReadTimeout());
    }

    @Test
    void sdkTimeoutsCannotBeDisabledOrInflatedWithoutBound() {
        AliyunSmsProperties properties = new AliyunSmsProperties();
        properties.setConnectTimeoutMs(0);
        properties.setReadTimeoutMs(Integer.MAX_VALUE);

        Config config = new AliyunSmsSender(properties).buildClientConfig();

        assertEquals(5000, config.getConnectTimeout());
        assertEquals(60000, config.getReadTimeout());
    }
}
