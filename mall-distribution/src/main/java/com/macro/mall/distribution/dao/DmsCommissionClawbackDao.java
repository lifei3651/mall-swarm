package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsCommissionClawbackDao {

    List<DmsCommissionClawback> selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default List<DmsCommissionClawback> selectByOrderId(Long orderId) {
        return selectByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    List<DmsCommissionClawback> selectByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default List<DmsCommissionClawback> selectByAgentId(Long agentId) {
        return selectByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    List<DmsCommissionClawback> selectPendingDebtByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default List<DmsCommissionClawback> selectPendingDebtByAgentId(Long agentId) {
        return selectPendingDebtByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    BigDecimal sumByCommissionRecordIdScoped(@Param("tenantId") Long tenantId, @Param("commissionRecordId") Long commissionRecordId);
    default BigDecimal sumByCommissionRecordId(Long commissionRecordId) {
        return sumByCommissionRecordIdScoped(TenantContext.getTenantId(), commissionRecordId);
    }

    BigDecimal sumDebtByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default BigDecimal sumDebtByAgentId(Long agentId) {
        return sumDebtByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    int updateDebtAfterOffsetScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                    @Param("deductedAmount") BigDecimal deductedAmount,
                                    @Param("debtAmount") BigDecimal debtAmount,
                                    @Param("status") Integer status);
    default int updateDebtAfterOffset(Long id, BigDecimal deductedAmount, BigDecimal debtAmount, Integer status) {
        return updateDebtAfterOffsetScoped(TenantContext.getTenantId(), id, deductedAmount, debtAmount, status);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("clawback") DmsCommissionClawback clawback);
    default int insert(DmsCommissionClawback clawback) {
        Long tenantId = TenantContext.getTenantId();
        if (clawback == null) throw new IllegalArgumentException("佣金追回流水不能为空");
        if (clawback.getTenantId() == null) clawback.setTenantId(tenantId);
        if (!tenantId.equals(clawback.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的佣金追回流水");
        return insertScoped(tenantId, clawback);
    }
}
