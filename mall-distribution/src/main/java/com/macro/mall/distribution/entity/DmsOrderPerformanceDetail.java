package com.macro.mall.distribution.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单业绩明细实体类
 * 记录每笔订单的业绩归因，支持追溯来源
 * 对应数据库表：dms_order_performance_detail
 */
@Data
@Schema(description = "订单业绩明细")
public class DmsOrderPerformanceDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID */
    @Schema(description = "id")
    private Long id;

    /** 订单ID */
    @Schema(description = "orderId")
    private Long orderId;

    /** 订单编号 */
    @Schema(description = "orderNo")
    private String orderNo;

    /** 订单金额 */
    @Schema(description = "orderAmount")
    private BigDecimal orderAmount;

    /** 下单时间 */
    @Schema(description = "orderTime")
    private LocalDateTime orderTime;

    /** 订单归属用户ID（谁卖的） */
    @Schema(description = "ownerUserId")
    private Long ownerUserId;

    /** 归属代理ID */
    @Schema(description = "ownerAgentId")
    private Long ownerAgentId;

    /** 归属代理名称 */
    @Schema(description = "ownerAgentName")
    private String ownerAgentName;

    /** 目标代理ID（业绩累加到谁） */
    @Schema(description = "targetAgentId")
    private Long targetAgentId;

    /** 目标代理名称 */
    @Schema(description = "targetAgentName")
    private String targetAgentName;

    /**
     * 关系层级
     * 0-自己，1-直属，2及以上为无限层间接关系
     */
    @Schema(description = "relationLevel")
    private Integer relationLevel;

    /** 商品ID */
    @Schema(description = "productId")
    private Long productId;

    /** 商品名称 */
    @Schema(description = "productName")
    private String productName;

    /** 商品分类ID */
    @Schema(description = "productCategoryId")
    private Long productCategoryId;

    /** 数量 */
    @Schema(description = "quantity")
    private Integer quantity;

    /** 商品金额 */
    @Schema(description = "productAmount")
    private BigDecimal productAmount;

    /**
     * 业绩类型
     * 1-个人业绩 2-团队业绩
     */
    @Schema(description = "performanceType")
    private Integer performanceType;

    /** 业绩金额 */
    @Schema(description = "performanceAmount")
    private BigDecimal performanceAmount;

    /**
     * 状态
     * 0-无效 1-有效 2-退款
     */
    @Schema(description = "status")
    private Integer status;

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
