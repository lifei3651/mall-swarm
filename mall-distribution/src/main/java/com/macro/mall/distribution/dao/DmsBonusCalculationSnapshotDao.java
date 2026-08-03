package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsBonusCalculationSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsBonusCalculationSnapshotDao {

    List<DmsBonusCalculationSnapshot> selectByOrderId(@Param("orderId") Long orderId);

    int insert(DmsBonusCalculationSnapshot snapshot);
}
