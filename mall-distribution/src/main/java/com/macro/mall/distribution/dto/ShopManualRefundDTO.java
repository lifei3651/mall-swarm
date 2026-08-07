package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 后台超期退款申请。商品数量始终用于记录实际受影响的盒数，金额模式只改变商品款的计算方式。
 */
@Data
public class ShopManualRefundDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** QUANTITY 按选中盒数按比例退款；AMOUNT 使用后台填写的商品退款金额。 */
    private String refundMode;

    /** 金额模式下填写的商品退款金额，不含运费。 */
    private BigDecimal productRefundAmount;

    /** 选择本次退款涉及的商品及盒数。 */
    private List<ShopAfterSaleItemDTO> items;

    private String reason;

    /** 1 仅退款；2 退货退款。后台超期退款默认仅退款。 */
    private Integer applyType;

    private Long operatorId;

    private String operatorName;
}
