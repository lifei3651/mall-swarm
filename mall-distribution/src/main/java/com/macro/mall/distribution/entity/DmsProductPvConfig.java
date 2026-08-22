package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    private Long skuId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称不能超过128个字")
    private String productName;

    @Size(max = 128, message = "规格名称不能超过128个字")
    private String skuName;

    @DecimalMin(value = "0", message = "PV不能小于0")
    @Digits(integer = 12, fraction = 2, message = "PV最多12位整数和2位小数")
    private BigDecimal pvValue;

    @DecimalMin(value = "0", message = "BV不能小于0")
    @Digits(integer = 12, fraction = 2, message = "BV最多12位整数和2位小数")
    private BigDecimal bvValue;

    @DecimalMin(value = "0", message = "成本金额不能小于0")
    @Digits(integer = 12, fraction = 2, message = "成本金额最多12位整数和2位小数")
    private BigDecimal costAmount;

    @Min(value = 0, message = "状态不正确")
    @Max(value = 1, message = "状态不正确")
    private Integer status;

    @Size(max = 500, message = "备注不能超过500个字")
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
