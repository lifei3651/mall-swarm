package com.macro.mall.distribution.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Locale;

/**
 * 避免雪花ID超过 JavaScript Number 安全整数范围后在前端丢失精度。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        JavascriptSafeLongSerializer serializer = new JavascriptSafeLongSerializer();
        return builder -> builder
                .serializerByType(Long.class, serializer)
                .serializerByType(Long.TYPE, serializer);
    }

    private static class JavascriptSafeLongSerializer extends JsonSerializer<Long> {

        private static final long MAX_SAFE_INTEGER = 9007199254740991L;

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            if (shouldWriteAsString(gen) || value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
                gen.writeString(value.toString());
                return;
            }
            gen.writeNumber(value);
        }

        private boolean shouldWriteAsString(JsonGenerator gen) {
            String fieldName = gen.getOutputContext().getCurrentName();
            if (fieldName == null) {
                return false;
            }
            String normalized = fieldName.toLowerCase(Locale.ROOT);
            return "id".equals(normalized) || normalized.endsWith("id") || normalized.endsWith("ids");
        }
    }
}
