package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LiveAnalyticsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long roomId;
    private String roomTitle;
    private Long uniqueViewers;
    private Long currentViewers;
    private Long averageDurationSeconds;
    private Long shareCount;
    private Long commentCount;
    private Long productClickCount;
    private Long paidOrderCount;
    private BigDecimal paidAmount;
    private BigDecimal viewerToClickRate;
    private BigDecimal clickToPaidRate;
}
