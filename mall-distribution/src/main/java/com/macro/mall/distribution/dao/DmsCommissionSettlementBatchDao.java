package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsCommissionSettlementBatchDao {
    DmsCommissionSettlementBatch selectById(@Param("id") Long id);
    List<DmsCommissionSettlementBatch> selectList(@Param("status") Integer status);
    int insert(DmsCommissionSettlementBatch batch);
    int markExecuted(@Param("id") Long id, @Param("settledCount") Integer settledCount,
                     @Param("skippedCount") Integer skippedCount, @Param("executorId") Long executorId,
                     @Param("executorName") String executorName, @Param("executeTime") LocalDateTime executeTime);
}
