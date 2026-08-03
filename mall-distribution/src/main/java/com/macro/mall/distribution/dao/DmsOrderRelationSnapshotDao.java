package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsOrderRelationSnapshotDao {
    List<DmsOrderRelationSnapshot> selectByOrderId(@Param("orderId") Long orderId);
    int insert(DmsOrderRelationSnapshot snapshot);
}
