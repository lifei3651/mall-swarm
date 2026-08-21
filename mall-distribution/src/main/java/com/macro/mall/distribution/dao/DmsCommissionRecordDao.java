package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 佣金记录Mapper接口
 */
@Mapper
public interface DmsCommissionRecordDao {

    List<DmsCommissionRecord> selectByQueryScoped(@Param("tenantId") Long tenantId,
                                                   @Param("query") CommissionQueryDTO query);
    default List<DmsCommissionRecord> selectByQuery(CommissionQueryDTO query) {
        return selectByQueryScoped(TenantContext.getTenantId(), query);
    }

    /**
     * 根据ID查询记录
     */
    DmsCommissionRecord selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsCommissionRecord selectById(Long id) {
        return selectByIdScoped(TenantContext.getTenantId(), id);
    }

    DmsCommissionRecord selectByIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsCommissionRecord selectByIdForUpdate(Long id) {
        return selectByIdForUpdateScoped(TenantContext.getTenantId(), id);
    }

    /**
     * 根据记录编号查询
     */
    DmsCommissionRecord selectByRecordNoScoped(@Param("tenantId") Long tenantId, @Param("recordNo") String recordNo);
    default DmsCommissionRecord selectByRecordNo(String recordNo) {
        return selectByRecordNoScoped(TenantContext.getTenantId(), recordNo);
    }

    /**
     * 根据订单ID查询佣金记录
     */
    List<DmsCommissionRecord> selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default List<DmsCommissionRecord> selectByOrderId(Long orderId) {
        return selectByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    /**
     * 根据订单编号查询佣金记录
     */
    List<DmsCommissionRecord> selectByOrderNoScoped(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);
    default List<DmsCommissionRecord> selectByOrderNo(String orderNo) {
        return selectByOrderNoScoped(TenantContext.getTenantId(), orderNo);
    }

    /**
     * 查询所有佣金记录
     */
    List<DmsCommissionRecord> selectAllScoped(@Param("tenantId") Long tenantId);
    default List<DmsCommissionRecord> selectAll() { return selectAllScoped(TenantContext.getTenantId()); }

    /**
     * 根据状态查询佣金记录
     */
    List<DmsCommissionRecord> selectByStatusScoped(@Param("tenantId") Long tenantId, @Param("status") Integer status);
    default List<DmsCommissionRecord> selectByStatus(Integer status) {
        return selectByStatusScoped(TenantContext.getTenantId(), status);
    }

    /**
     * 根据代理ID查询佣金记录
     */
    List<DmsCommissionRecord> selectByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default List<DmsCommissionRecord> selectByAgentId(Long agentId) {
        return selectByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    /**
     * 根据代理ID和状态查询佣金记录
     */
    List<DmsCommissionRecord> selectByAgentIdAndStatusScoped(@Param("tenantId") Long tenantId,
                                                             @Param("agentId") Long agentId,
                                                             @Param("status") Integer status);
    default List<DmsCommissionRecord> selectByAgentIdAndStatus(Long agentId, Integer status) {
        return selectByAgentIdAndStatusScoped(TenantContext.getTenantId(), agentId, status);
    }

    List<DmsCommissionRecord> selectPendingByCreateTimeScoped(@Param("tenantId") Long tenantId,
                                                               @Param("periodStart") LocalDateTime periodStart,
                                                               @Param("periodEnd") LocalDateTime periodEnd,
                                                               @Param("cutoffTime") LocalDateTime cutoffTime);
    default List<DmsCommissionRecord> selectPendingByCreateTime(LocalDateTime periodStart, LocalDateTime periodEnd,
                                                                 LocalDateTime cutoffTime) {
        return selectPendingByCreateTimeScoped(TenantContext.getTenantId(), periodStart, periodEnd, cutoffTime);
    }

    List<DmsCommissionRecord> selectEligibleForCoolingOffSettlementScoped(@Param("tenantId") Long tenantId,
                                                                           @Param("receivedCutoff") LocalDateTime receivedCutoff,
                                                                           @Param("limit") Integer limit);
    default List<DmsCommissionRecord> selectEligibleForCoolingOffSettlement(LocalDateTime receivedCutoff, Integer limit) {
        return selectEligibleForCoolingOffSettlementScoped(TenantContext.getTenantId(), receivedCutoff, limit);
    }

    /**
     * 查询代理的待结算佣金总额
     */
    BigDecimal selectUnsettledAmountByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default BigDecimal selectUnsettledAmountByAgentId(Long agentId) {
        return selectUnsettledAmountByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    /**
     * 查询代理的已结算佣金总额
     */
    BigDecimal selectSettledAmountByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default BigDecimal selectSettledAmountByAgentId(Long agentId) {
        return selectSettledAmountByAgentIdScoped(TenantContext.getTenantId(), agentId);
    }

    /**
     * 插入记录
     */
    int insertScoped(@Param("tenantId") Long tenantId, @Param("record") DmsCommissionRecord record);
    default int insert(DmsCommissionRecord record) {
        Long tenantId = TenantContext.getTenantId();
        if (record == null) throw new IllegalArgumentException("佣金记录不能为空");
        if (record.getTenantId() == null) record.setTenantId(tenantId);
        if (!tenantId.equals(record.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的佣金记录");
        return insertScoped(tenantId, record);
    }

    /**
     * 更新记录
     */
    int updateScoped(@Param("tenantId") Long tenantId, @Param("record") DmsCommissionRecord record);
    default int update(DmsCommissionRecord record) {
        return updateScoped(TenantContext.getTenantId(), record);
    }

    /**
     * 更新记录状态
     */
    int updateStatusScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);
    default int updateStatus(Long id, Integer status) {
        return updateStatusScoped(TenantContext.getTenantId(), id, status);
    }

    /**
     * 更新佣金金额和状态
     */
    int updateAmountAndStatusScoped(@Param("tenantId") Long tenantId,
                                    @Param("id") Long id,
                                    @Param("commissionAmount") BigDecimal commissionAmount,
                                    @Param("status") Integer status,
                                    @Param("remark") String remark);
    default int updateAmountAndStatus(Long id, BigDecimal commissionAmount, Integer status, String remark) {
        return updateAmountAndStatusScoped(TenantContext.getTenantId(), id, commissionAmount, status, remark);
    }

    /**
     * 批量更新记录状态
     */
    int updateStatusBatchScoped(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids,
                                @Param("status") Integer status);
    default int updateStatusBatch(List<Long> ids, Integer status) {
        return updateStatusBatchScoped(TenantContext.getTenantId(), ids, status);
    }

    /**
     * 删除记录
     */
    int deleteByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int deleteById(Long id) { return deleteByIdScoped(TenantContext.getTenantId(), id); }
}
