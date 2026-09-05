package com.macro.mall.distribution.vo;

import com.macro.mall.common.api.CommonPage;

public record WeChatShippingOperationsVO(boolean enabled, long failedCount, CommonPage<WeChatShippingTaskVO> tasks) { }
