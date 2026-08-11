package com.macro.mall.distribution.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc配置
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("分销分佣系统API")
                        .version("v1")
                        .description("灵启商城后端接口文档；浏览器端优先使用 /api/v1 前缀，旧 /api 前缀兼容保留"));
    }
}
