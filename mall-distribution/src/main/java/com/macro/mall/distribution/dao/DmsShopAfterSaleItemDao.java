package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsShopAfterSaleItemDao {
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(i.refund_quantity),0) FROM dms_shop_after_sale_item i JOIN dms_shop_after_sale s ON s.id=i.after_sale_id WHERE i.order_item_id=#{orderItemId} AND s.apply_type IN (1,2,4) AND s.status IN (1,6)")
    int sumRefundedQuantityByOrderItemId(@org.apache.ibatis.annotations.Param("orderItemId") Long orderItemId);
    List<DmsShopAfterSaleItem> selectByAfterSaleId(@Param("afterSaleId") Long afterSaleId);

    /** Validate reserved history before refund or stock mutation. */
    int countInvalidReservedItemsByOrderId(@Param("orderId") Long orderId);

    int sumReservedQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);

    int sumApprovedQuantityByOrderId(@Param("orderId") Long orderId);

    /** 只累计渠道及本地账务均已完成的退款数量；处理中退款仅用于占用可退/可发额度。 */
    int sumCompletedQuantityByOrderId(@Param("orderId") Long orderId);

    BigDecimal sumApprovedProductRefundByOrderId(@Param("orderId") Long orderId);

    /** 只累计已完成退款中、下单快照参与团队奖金的商品退款金额。 */
    BigDecimal sumApprovedBonusRefundByOrderId(@Param("orderId") Long orderId);

    /** 按订单SKU/商品下单时冻结的单位成本，只计算已完成退款对应成本。 */
    BigDecimal sumApprovedCostByOrderId(@Param("orderId") Long orderId);

    int insertBatch(@Param("items") List<DmsShopAfterSaleItem> items);
}
