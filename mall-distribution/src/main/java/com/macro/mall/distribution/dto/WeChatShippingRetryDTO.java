package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 按页面读到的版本比较并交换，重复点击不能重新排队。 */
public record WeChatShippingRetryDTO(@NotNull @Min(1) Integer revision) { }
