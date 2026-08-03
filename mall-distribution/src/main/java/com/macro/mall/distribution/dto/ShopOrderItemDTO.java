package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopOrderItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    private Long skuId;

    private Integer quantity;
}
