package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopOrderShipmentDao {

    List<DmsShopOrderShipment> selectByOrderId(@Param("orderId") Long orderId);

    DmsShopOrderShipment selectByOrderAndTracking(@Param("orderId") Long orderId,
                                                   @Param("deliveryCompany") String deliveryCompany,
                                                   @Param("deliveryNo") String deliveryNo);

    int sumQuantityByOrderId(@Param("orderId") Long orderId);

    int insert(DmsShopOrderShipment shipment);
}
