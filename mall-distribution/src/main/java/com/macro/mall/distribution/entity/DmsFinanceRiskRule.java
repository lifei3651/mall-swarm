package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsFinanceRiskRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "风险规则编码不能为空")
    @Size(max = 64, message = "风险规则编码不能超过64个字")
    private String ruleCode;

    @NotBlank(message = "风险规则名称不能为空")
    @Size(max = 128, message = "风险规则名称不能超过128个字")
    private String ruleName;

    @DecimalMin(value = "0", message = "风险阈值不能小于0")
    @Digits(integer = 14, fraction = 2, message = "风险阈值格式不正确")
    private BigDecimal thresholdValue;

    @Min(value = 0, message = "启用状态不正确")
    @Max(value = 1, message = "启用状态不正确")
    private Integer enabled;

    @Size(max = 500, message = "备注不能超过500个字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
