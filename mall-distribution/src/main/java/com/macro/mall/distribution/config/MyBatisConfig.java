package com.macro.mall.distribution.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis配置
 */
@Configuration
public class MyBatisConfig {

    @Bean
    public TenantLineMyBatisInterceptor tenantLineMyBatisInterceptor() {
        return new TenantLineMyBatisInterceptor();
    }
}
