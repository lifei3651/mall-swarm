package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderPvDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsOrderPvDetailDao {

    List<DmsOrderPvDetail> selectByOrderId(@Param("orderId") Long orderId);

    int insert(DmsOrderPvDetail detail);
}
