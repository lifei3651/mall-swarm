package com.macro.mall.distribution.config;

import com.macro.mall.distribution.security.EncryptedStringTypeHandler;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 在 MyBatis 解析 Mapper 之前装载数据库字段加密密钥。 */
@Configuration(proxyBeanMethods = false)
public class DataEncryptionKeyConfiguration {

    @Bean
    static BeanFactoryPostProcessor dataEncryptionKeyInitializer(Environment environment) {
        String key = environment.getProperty("security.data-encryption-key", "");
        boolean writeEnabled = environment.getProperty(
                "security.data-encryption.write-enabled", Boolean.class, false);
        return beanFactory -> EncryptedStringTypeHandler.configure(key, writeEnabled);
    }
}
