package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代理信息VO
 */
@Data
public class AgentInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 代理ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商城登录账号（字段名为兼容旧接口保留）。 */
    private String memberAccount;

    /** 代理编号 */
    private String agentCode;

    /** 代理名称 */
    private String agentName;

    /** 代理等级 */
    private Integer agentLevel;

    /** 代理等级名称 */
    private String agentLevelName;

    /** 直属上级代理ID */
    private Long parentId;

    /** 上级代理名称 */
    private String parentName;

    /** 是否存在尚未处理或尚未生效的历史移线申请。 */
    private Boolean hasPendingLineChange;

    /** 层级深度 */
    private Integer levelDepth;

    /** 邀请码 */
    private String inviteCode;

    /** 推广二维码URL */
    private String qrCodeUrl;

    /** 手机号 */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 状态 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 来源类型 */
    private Integer sourceType;

    /** 来源类型名称 */
    private String sourceTypeName;

    /** 本人及团队当月有效商品业绩。 */
    private BigDecimal currentMonthPerformance;

    /** 历史累计奖金。 */
    private BigDecimal totalCommission;

    /** 当前有效无限层下级人数。 */
    private Integer teamMemberCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
