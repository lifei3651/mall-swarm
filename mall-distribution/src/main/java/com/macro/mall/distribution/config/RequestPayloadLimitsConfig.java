package com.macro.mall.distribution.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为所有 JSON 接口提供最后一道体积边界，防止遗漏字段校验时接收超长字符串或异常深层结构。
 * 具体数据库字段仍必须使用 DTO/实体上的 Bean Validation 声明更小、更准确的上限。
 */
@Configuration
public class RequestPayloadLimitsConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer requestPayloadLimitsCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxStringLength(65_536)
                        .maxNestingDepth(64)
                        .maxNumberLength(1_000)
                        .build()));
    }
}
