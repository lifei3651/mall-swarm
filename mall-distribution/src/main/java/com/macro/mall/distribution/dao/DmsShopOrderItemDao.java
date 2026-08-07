package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopOrderItemDao {

    List<DmsShopOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    int sumQuantityByOrderId(@Param("orderId") Long orderId);

    /** 查询会员在未关闭订单中已占用的某商品数量，待付款订单也会占用限购额度。 */
    int sumQuantityByUserAndProduct(@Param("userId") Long userId,
                                    @Param("productId") Long productId,
                                    @Param("tenantId") Long tenantId);

    int insert(DmsShopOrderItem item);
}
