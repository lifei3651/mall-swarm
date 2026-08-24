package com.macro.mall.distribution.service;

import java.time.LocalDateTime;

/** 已发生业务事实的最小快照；绝不携带金额、账号、地址、手机号、原因或验证码。 */
public record MemberMessageEvent(Long tenantId, Long userId, String eventKey, String eventType,
                                 String category, String targetType, Long targetId,
                                 Long targetParentId, LocalDateTime occurredTime) {
}
