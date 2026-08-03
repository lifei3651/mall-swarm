package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsCommissionClawback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsCommissionClawbackDao {

    List<DmsCommissionClawback> selectByOrderId(@Param("orderId") Long orderId);

    List<DmsCommissionClawback> selectByAgentId(@Param("agentId") Long agentId);

    List<DmsCommissionClawback> selectPendingDebtByAgentId(@Param("agentId") Long agentId);

    BigDecimal sumByCommissionRecordId(@Param("commissionRecordId") Long commissionRecordId);

    BigDecimal sumDebtByAgentId(@Param("agentId") Long agentId);

    int updateDebtAfterOffset(@Param("id") Long id,
                              @Param("deductedAmount") BigDecimal deductedAmount,
                              @Param("debtAmount") BigDecimal debtAmount,
                              @Param("status") Integer status);

    int insert(DmsCommissionClawback clawback);
}
