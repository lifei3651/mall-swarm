package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsFlashSaleActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private String activityName;
    private Long productId;
    private Long skuId;
    private BigDecimal flashPrice;
    private BigDecimal flashPv;
    private Integer totalStock;
    private Integer availableStock;
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0草稿、1启用、2停用。 */
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
