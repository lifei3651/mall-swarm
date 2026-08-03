package com.macro.mall.distribution.dao;

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

    List<DmsCommissionRecord> selectByQuery(CommissionQueryDTO query);

    /**
     * 根据ID查询记录
     */
    DmsCommissionRecord selectById(@Param("id") Long id);

    DmsCommissionRecord selectByIdForUpdate(@Param("id") Long id);

    /**
     * 根据记录编号查询
     */
    DmsCommissionRecord selectByRecordNo(@Param("recordNo") String recordNo);

    /**
     * 根据订单ID查询佣金记录
     */
    List<DmsCommissionRecord> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单编号查询佣金记录
     */
    List<DmsCommissionRecord> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询所有佣金记录
     */
    List<DmsCommissionRecord> selectAll();

    /**
     * 根据状态查询佣金记录
     */
    List<DmsCommissionRecord> selectByStatus(@Param("status") Integer status);

    /**
     * 根据代理ID查询佣金记录
     */
    List<DmsCommissionRecord> selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据代理ID和状态查询佣金记录
     */
    List<DmsCommissionRecord> selectByAgentIdAndStatus(@Param("agentId") Long agentId, @Param("status") Integer status);

    List<DmsCommissionRecord> selectPendingByCreateTime(@Param("periodStart") LocalDateTime periodStart,
                                                         @Param("periodEnd") LocalDateTime periodEnd,
                                                         @Param("cutoffTime") LocalDateTime cutoffTime);

    List<DmsCommissionRecord> selectEligibleForCoolingOffSettlement(@Param("receivedCutoff") LocalDateTime receivedCutoff,
                                                                     @Param("limit") Integer limit);

    /**
     * 查询代理的待结算佣金总额
     */
    BigDecimal selectUnsettledAmountByAgentId(@Param("agentId") Long agentId);

    /**
     * 查询代理的已结算佣金总额
     */
    BigDecimal selectSettledAmountByAgentId(@Param("agentId") Long agentId);

    /**
     * 插入记录
     */
    int insert(DmsCommissionRecord record);

    /**
     * 更新记录
     */
    int update(DmsCommissionRecord record);

    /**
     * 更新记录状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新佣金金额和状态
     */
    int updateAmountAndStatus(@Param("id") Long id,
                              @Param("commissionAmount") BigDecimal commissionAmount,
                              @Param("status") Integer status,
                              @Param("remark") String remark);

    /**
     * 批量更新记录状态
     */
    int updateStatusBatch(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     * 删除记录
     */
    int deleteById(@Param("id") Long id);
}
