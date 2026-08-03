package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsFinanceRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsFinanceRefundDao {

    List<DmsFinanceRefund> selectByOrderId(@Param("orderId") Long orderId);

    BigDecimal sumByOrderId(@Param("orderId") Long orderId);

    BigDecimal sumProductByOrderId(@Param("orderId") Long orderId);

    BigDecimal sumFreightByOrderId(@Param("orderId") Long orderId);

    int sumQuantityByOrderId(@Param("orderId") Long orderId);

    int insert(DmsFinanceRefund refund);
}
