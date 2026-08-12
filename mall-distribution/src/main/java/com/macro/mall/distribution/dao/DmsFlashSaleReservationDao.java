package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsFlashSaleReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsFlashSaleReservationDao {
    DmsFlashSaleReservation selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);
    DmsFlashSaleReservation selectByOrderId(@Param("orderId") Long orderId);
    int insert(DmsFlashSaleReservation reservation);
    int reactivate(DmsFlashSaleReservation reservation);
    int bindOrder(DmsFlashSaleReservation reservation);
    int updateStatusByOrder(@Param("orderId") Long orderId, @Param("status") String status);
    int releaseByOrder(@Param("orderId") Long orderId);
    int releaseRefundedQuantity(@Param("orderId") Long orderId, @Param("quantity") Integer quantity);
}
