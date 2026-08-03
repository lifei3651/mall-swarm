package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

/** 前台选择的实际退货商品及数量；金额必须由服务端按订单实付计算。 */
@Data
public class ShopAfterSaleItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderItemId;
    private Integer quantity;
}
