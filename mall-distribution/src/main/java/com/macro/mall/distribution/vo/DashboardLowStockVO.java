package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

/** 工作台低库存/缺货预警。 */
@Data
public class DashboardLowStockVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private String productName;
    private Long skuId;
    private String skuName;
    private Integer stock;
    private Integer safetyStock;
}
