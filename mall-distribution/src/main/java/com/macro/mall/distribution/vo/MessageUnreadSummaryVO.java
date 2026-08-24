package com.macro.mall.distribution.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MessageUnreadSummaryVO {
    private long total;
    private Map<String, Long> categories = new LinkedHashMap<>();
}
