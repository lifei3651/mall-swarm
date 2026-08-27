package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 佣金记录实体类
 * 对应数据库表：dms_commission_record
 */
@Data
@Schema(description = "佣金记录信息")
public class DmsCommissionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户公司ID */
    @Schema(description = "tenantId")
    private Long tenantId;

    /** 当前客户项目的奖金程序版本ID */
    @Schema(description = "ruleVersionId")
    private Long ruleVersionId;

    /** 记录ID */
    @Schema(description = "id")
    private Long id;

    /** 记录编号（唯一） */
    @Schema(description = "recordNo")
    private String recordNo;

    /** 订单ID */
    @Schema(description = "orderId")
    private Long orderId;

    /** 订单编号 */
    @Schema(description = "orderNo")
    private String orderNo;

    /** 订单金额 */
    @Schema(description = "orderAmount")
    private BigDecimal orderAmount;

    /** 下单用户ID */
    @Schema(description = "orderUserId")
    private Long orderUserId;

    /** 下单用户名称 */
    @Schema(description = "orderUserName")
    private String orderUserName;

    /** 获得佣金的代理ID */
    @Schema(description = "agentId")
    private Long agentId;

    /** 代理用户ID */
    @Schema(description = "agentUserId")
    private Long agentUserId;

    /** 代理名称 */
    @Schema(description = "agentName")
    private String agentName;

    /** 代理等级 */
    @Schema(description = "agentLevel")
    private Integer agentLevel;

    /** 与下单人的关系深度；直推为1，团队分红可以是任意正整数。 */
    @Schema(description = "commissionLevel")
    private Integer commissionLevel;

    /** 客户奖金程序返回的类型编码；商城基座不预设具体制度。 */
    @Schema(description = "bonusType")
    private String bonusType;

    /** 佣金比例 */
    @Schema(description = "commissionRate")
    private BigDecimal commissionRate;

    /** 佣金金额 */
    @Schema(description = "commissionAmount")
    private BigDecimal commissionAmount;

    /**
     * 状态
     * 0-待结算 1-已结算 2-已取消 3-已退款
     */
    @Schema(description = "status")
    private Integer status;

    /** 结算时间 */
    @Schema(description = "settleTime")
    private LocalDateTime settleTime;

    /** 取消原因 */
    @Schema(description = "cancelReason")
    private String cancelReason;

    /** 备注 */
    @Schema(description = "remark")
    private String remark;

    /** 创建时间 */
    @Schema(description = "createTime")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Schema(description = "updateTime")
    private LocalDateTime updateTime;
}
