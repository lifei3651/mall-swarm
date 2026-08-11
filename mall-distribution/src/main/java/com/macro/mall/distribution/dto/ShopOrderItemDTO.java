package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopOrderItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "商品不能为空")
    private Long productId;

    private Long skuId;

    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于0")
    private Integer quantity;
}
