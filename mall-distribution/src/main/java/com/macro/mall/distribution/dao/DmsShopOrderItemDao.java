package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopOrderItemDao {

    List<DmsShopOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    int sumQuantityByOrderId(@Param("orderId") Long orderId);

    int insert(DmsShopOrderItem item);
}
