package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsShopAfterSaleItemDao {
    List<DmsShopAfterSaleItem> selectByAfterSaleId(@Param("afterSaleId") Long afterSaleId);

    /** Validate reserved history before refund or stock mutation. */
    int countInvalidReservedItemsByOrderId(@Param("orderId") Long orderId);

    int sumReservedQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);

    int sumApprovedQuantityByOrderId(@Param("orderId") Long orderId);

    BigDecimal sumApprovedProductRefundByOrderId(@Param("orderId") Long orderId);

    /** 只累计下单快照中参与团队奖金的商品退款金额。 */
    BigDecimal sumApprovedBonusRefundByOrderId(@Param("orderId") Long orderId);

    /** 按订单SKU/商品下单时冻结的单位成本，计算已审批退货对应成本。 */
    BigDecimal sumApprovedCostByOrderId(@Param("orderId") Long orderId);

    int insertBatch(@Param("items") List<DmsShopAfterSaleItem> items);
}
