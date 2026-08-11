package com.macro.mall.common.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class SensitiveMessageConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveLogSanitizer.sanitizeText(event.getFormattedMessage());
    }
}
