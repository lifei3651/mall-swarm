package com.macro.mall.distribution.service;

public record MemberMessageCreatedEvent(Long tenantId, Long userId, Long messageId) {
}
