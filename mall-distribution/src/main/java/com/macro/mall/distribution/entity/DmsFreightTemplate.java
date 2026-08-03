package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 中国省市区运费模板。 */
@Data
public class DmsFreightTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String templateName;
    /** FREE-包邮，FIXED-固定运费，UNAVAILABLE-不发货。 */
    private String defaultMode;
    private BigDecimal defaultFreightAmount;
    /** 地区特例规则 JSON，详见 FreightTemplateRuleDTO。 */
    private String rulesJson;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
