package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DmsShopSku implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    private String skuNo;

    private String skuName;

    private String attrsJson;

    private String imageUrl;

    private BigDecimal salePrice;

    private BigDecimal marketPrice;

    private BigDecimal costAmount;

    private BigDecimal pvValue;

    /** 多规格商品的独立复购价和复购PV；为空时继承商品级配置。 */
    private BigDecimal repurchasePrice;

    private BigDecimal repurchasePv;

    private BigDecimal bvValue;

    private Integer stock;

    /** SKU 级安全库存。 */
    private Integer safetyStock;

    private Integer salesCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
