package com.macro.mall.common.log;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class SensitiveThrowableProxyConverter extends ThrowableProxyConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveLogSanitizer.sanitizeText(super.convert(event));
    }
}
