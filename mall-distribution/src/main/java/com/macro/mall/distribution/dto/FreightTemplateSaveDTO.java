package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FreightTemplateSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    @NotBlank(message = "运费模板名称不能为空")
    @Size(max = 128, message = "运费模板名称不能超过128个字")
    private String templateName;
    @Size(max = 16, message = "默认运费模式不正确")
    private String defaultMode;
    private BigDecimal defaultFreightAmount;
    @Size(max = 200, message = "单个运费模板最多维护200条地区规则")
    private List<@Valid FreightTemplateRuleDTO> rules;
    private Integer status;
}
