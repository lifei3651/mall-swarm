package com.macro.mall.distribution.notification;

public record NotificationReceipt(String receiptId, Long taskId, String status,
                                  String providerMessageId, String errorCode) { }
