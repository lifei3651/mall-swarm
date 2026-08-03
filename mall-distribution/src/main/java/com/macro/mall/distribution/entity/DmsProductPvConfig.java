package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 PV/BV/成本配置
 */
@Data
public class DmsProductPvConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuName;

    private BigDecimal pvValue;

    private BigDecimal bvValue;

    private BigDecimal costAmount;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
