package com.macro.mall.distribution.vo;

import java.time.LocalDateTime;

public record WeChatShippingTaskVO(String id, String paymentNoHint, String status, Integer revision,
                                  Integer syncedRevision, Integer attemptCount, String errorCode,
                                  LocalDateTime nextRetryTime, LocalDateTime syncedTime,
                                  LocalDateTime updateTime, boolean canRetry) { }
