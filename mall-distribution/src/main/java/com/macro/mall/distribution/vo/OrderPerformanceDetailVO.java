package com.macro.mall.distribution.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单业绩明细VO
 */
@Data
public class OrderPerformanceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 订单归属用户ID */
    private Long ownerUserId;

    /** 归属代理ID */
    private Long ownerAgentId;

    /** 归属代理名称 */
    private String ownerAgentName;

    /** 商品名称 */
    private String productName;

    /** 商品数量 */
    private Integer quantity;

    /** 业绩金额 */
    private BigDecimal performanceAmount;

    /** 关系层级 */
    private Integer relationLevel;

    /** 关系层级名称 */
    private String relationLevelName;

    /** 业绩类型：1-个人，2-团队；状态：1-有效，0-冲正/无效 */
    private Integer performanceType;
    private Integer status;
    private String remark;
}
