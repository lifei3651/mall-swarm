package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 导入订单DTO
 */
@Data
public class ImportOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单编号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 订单归属登录账号；兼容历史推广编号。 */
    private String ownerAgentCode;

    /** 商品名称 */
    private String productName;

    /** 商品数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;
}
