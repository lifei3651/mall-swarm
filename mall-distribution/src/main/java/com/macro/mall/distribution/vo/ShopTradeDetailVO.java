package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopTrade;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 平台查看的一次联合支付及其全部商户履约子订单。 */
@Data
public class ShopTradeDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private DmsShopTrade trade;
    private List<ShopOrderVO> childOrders;
    private Integer childCount;
    private BigDecimal childPayAmount;
    private BigDecimal refundedAmount;
}
