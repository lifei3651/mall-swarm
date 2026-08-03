package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 业绩查询DTO
 */
@Data
public class PerformanceQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long agentId;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 统计类型：1-日 2-周 3-月 4-年 */
    private Integer statType;

    /** 关系层级筛选 */
    private Integer relationLevel;
}
