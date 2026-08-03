package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/** 外部平台团队平移行。历史数据只做期初数，不参与历史奖金重算。 */
@Data
public class ExternalTeamMemberDTO implements Serializable {
    private String externalMemberCode;
    private String phone;
    private String nickname;
    private String parentExternalCode;
    private Integer initialLevel;
    private Integer historicalOrderCount;
    private BigDecimal historicalPersonalPerformance;
    private BigDecimal historicalTeamPerformance;
    private String remark;
}
