package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class FreightTemplateRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 多个省/市/区路径，如 [["西藏自治区"],["海南省","三沙市"]]。 */
    private List<List<String>> regionPaths;
    /** FREE-包邮，FIXED-额外运费，UNAVAILABLE-不发货。 */
    private String mode;
    private BigDecimal freightAmount;
}
