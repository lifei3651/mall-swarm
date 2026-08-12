package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ShopSkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    @Size(max = 64, message = "SKU编码不能超过64个字符")
    private String skuNo;

    @NotBlank(message = "SKU名称不能为空")
    @Size(max = 128, message = "SKU名称不能超过128个字")
    private String skuName;

    @Size(max = 10000, message = "SKU规格属性内容过长")
    private String attrsJson;

    @Size(max = 2048, message = "SKU图片地址不能超过2048个字符")
    private String imageUrl;

    private BigDecimal salePrice;

    private BigDecimal marketPrice;

    private BigDecimal costAmount;

    private BigDecimal pvValue;

    @PositiveOrZero(message = "SKU复购价不能小于0")
    private BigDecimal repurchasePrice;

    @PositiveOrZero(message = "SKU复购PV不能小于0")
    private BigDecimal repurchasePv;

    private BigDecimal bvValue;

    @PositiveOrZero(message = "SKU库存不能小于0")
    private Integer stock;

    @PositiveOrZero(message = "SKU安全库存不能小于0")
    private Integer safetyStock;

    private Integer status;
}
