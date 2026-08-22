package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 客户可配置的基础内容词库；为空时不改变商城基座现有内容。 */
@Service
public class ContentModerationService {

    private final List<String> blockedTerms;

    public ContentModerationService(@Value("${shop.content.blocked-terms:}") String configuredTerms) {
        this.blockedTerms = Arrays.stream(configuredTerms == null ? new String[0] : configuredTerms.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(1000)
                .toList();
    }

    public void assertAllowed(String fieldName, String content) {
        if (content == null || content.isBlank() || blockedTerms.isEmpty()) return;
        String normalized = content.toLowerCase(Locale.ROOT);
        if (blockedTerms.stream().anyMatch(normalized::contains)) {
            Asserts.fail(fieldName + "包含商城禁止发布的内容，请修改后重试");
        }
    }
}
