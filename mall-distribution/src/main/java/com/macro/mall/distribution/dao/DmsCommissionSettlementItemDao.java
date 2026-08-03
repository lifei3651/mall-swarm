package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsCommissionSettlementItemDao {
    List<DmsCommissionSettlementItem> selectByBatchId(@Param("batchId") Long batchId);
    int insertBatch(@Param("items") List<DmsCommissionSettlementItem> items);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("skipReason") String skipReason);
}
