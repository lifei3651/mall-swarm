package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ShopAfterSaleApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    private Integer applyType;

    private BigDecimal refundAmount;

    /** 实际申请退回的订单商品和数量；退款金额由服务端计算。 */
    private List<ShopAfterSaleItemDTO> items;

    private String reason;

    private String proofImages;
}
