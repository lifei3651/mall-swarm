package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/** 前台选择的实际退货商品及数量；金额必须由服务端按订单实付计算。 */
@Data
public class ShopAfterSaleItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "售后商品不能为空")
    private Long orderItemId;
    @NotNull(message = "退货数量不能为空")
    @Positive(message = "退货数量必须大于0")
    private Integer quantity;
}
