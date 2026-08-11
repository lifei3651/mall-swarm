package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FreightTemplateRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 多个省/市/区路径，如 [["西藏自治区"],["海南省","三沙市"]]。 */
    @Size(max = 200, message = "单条运费规则最多选择200个地区")
    private List<List<String>> regionPaths;
    /** FREE-包邮，FIXED-额外运费，UNAVAILABLE-不发货。 */
    @NotBlank(message = "运费规则模式不能为空")
    @Size(max = 16, message = "运费规则模式不正确")
    private String mode;
    private BigDecimal freightAmount;
}
