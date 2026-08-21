package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsCommissionSettlementBatchDao {
    DmsCommissionSettlementBatch selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsCommissionSettlementBatch selectById(Long id) {
        return selectByIdScoped(TenantContext.getTenantId(), id);
    }
    List<DmsCommissionSettlementBatch> selectListScoped(@Param("tenantId") Long tenantId, @Param("status") Integer status);
    default List<DmsCommissionSettlementBatch> selectList(Integer status) {
        return selectListScoped(TenantContext.getTenantId(), status);
    }
    int insertScoped(@Param("tenantId") Long tenantId, @Param("batch") DmsCommissionSettlementBatch batch);
    default int insert(DmsCommissionSettlementBatch batch) {
        Long tenantId = TenantContext.getTenantId();
        if (batch == null) throw new IllegalArgumentException("佣金结算批次不能为空");
        if (batch.getTenantId() == null) batch.setTenantId(tenantId);
        if (!tenantId.equals(batch.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的佣金结算批次");
        return insertScoped(tenantId, batch);
    }
    int markExecutedScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                           @Param("settledCount") Integer settledCount,
                           @Param("skippedCount") Integer skippedCount, @Param("executorId") Long executorId,
                           @Param("executorName") String executorName, @Param("executeTime") LocalDateTime executeTime);
    default int markExecuted(Long id, Integer settledCount, Integer skippedCount, Long executorId,
                             String executorName, LocalDateTime executeTime) {
        return markExecutedScoped(TenantContext.getTenantId(), id, settledCount, skippedCount, executorId,
                executorName, executeTime);
    }
}
