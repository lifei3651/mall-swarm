package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FreightTemplateSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String templateName;
    private String defaultMode;
    private BigDecimal defaultFreightAmount;
    private List<FreightTemplateRuleDTO> rules;
    private Integer status;
}
