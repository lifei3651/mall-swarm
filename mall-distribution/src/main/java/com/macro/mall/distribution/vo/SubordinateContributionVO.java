package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 下属业绩贡献VO
 */
@Data
public class SubordinateContributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 下属代理ID */
    private Long subordinateAgentId;

    /** 下属名称 */
    private String subordinateName;

    /** 下属会员登录账号。 */
    private String subordinateMemberAccount;

    /** 关系层级 */
    private Integer relationLevel;

    /** 关系层级名称 */
    private String relationLevelName;

    /** 贡献业绩金额（自己+团队） */
    private BigDecimal contributionAmount;

    /** 贡献有效商品件数 */
    private Integer orderCount;

    /** 下属自己的业绩 */
    private BigDecimal selfPerformance;

    /** 下属自己的有效商品件数 */
    private Integer selfOrderCount;

    /** 下属团队的业绩 */
    private BigDecimal teamPerformance;

    /** 下属无限层团队的有效商品件数 */
    private Integer teamOrderCount;

    /** 下属的下级数量 */
    private Integer subordinateCount;
}
