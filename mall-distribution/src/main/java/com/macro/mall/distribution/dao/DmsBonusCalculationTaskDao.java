package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsBonusCalculationTaskDao {

    DmsBonusCalculationTask selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default DmsBonusCalculationTask selectById(Long id) {
        return selectByIdScoped(TenantContext.getTenantId(), id);
    }

    DmsBonusCalculationTask selectLatestByOrderIdScoped(@Param("tenantId") Long tenantId,
                                                        @Param("orderId") Long orderId);

    default DmsBonusCalculationTask selectLatestByOrderId(Long orderId) {
        return selectLatestByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    List<DmsBonusCalculationTask> selectListScoped(@Param("tenantId") Long tenantId,
                                                   @Param("status") Integer status,
                                                   @Param("orderId") Long orderId);

    default List<DmsBonusCalculationTask> selectList(Integer status, Long orderId) {
        return selectListScoped(TenantContext.getTenantId(), status, orderId);
    }

    List<DmsBonusCalculationTask> selectExecutableScoped(@Param("tenantId") Long tenantId,
                                                         @Param("limit") Integer limit);

    default List<DmsBonusCalculationTask> selectExecutable(Integer limit) {
        return selectExecutableScoped(TenantContext.getTenantId(), limit);
    }

    int insert(DmsBonusCalculationTask task);

    int markProcessingScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default int markProcessing(Long id) {
        return markProcessingScoped(TenantContext.getTenantId(), id);
    }

    int markSuccessScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default int markSuccess(Long id) {
        return markSuccessScoped(TenantContext.getTenantId(), id);
    }

    int markFailedScoped(@Param("tenantId") Long tenantId,
                         @Param("id") Long id,
                         @Param("failReason") String failReason,
                         @Param("nextRetryTime") LocalDateTime nextRetryTime);

    default int markFailed(Long id, String failReason, LocalDateTime nextRetryTime) {
        return markFailedScoped(TenantContext.getTenantId(), id, failReason, nextRetryTime);
    }
}
