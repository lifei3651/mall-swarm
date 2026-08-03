package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOrderBalanceAllocationDao {

    List<DmsOrderBalanceAllocation> selectByOrderId(@Param("orderId") Long orderId);

    DmsOrderBalanceAllocation selectByOrderIdAndTypeForUpdate(@Param("orderId") Long orderId,
                                                               @Param("allocationType") String allocationType);

    DmsOrderBalanceAllocation selectByIdForUpdate(@Param("id") Long id);

    List<Long> selectMissingOrderIds(@Param("tenantId") Long tenantId,
                                     @Param("limit") int limit);

    List<Long> selectEligibleIds(@Param("tenantId") Long tenantId,
                                 @Param("receivedCutoff") LocalDateTime receivedCutoff,
                                 @Param("limit") int limit);

    int insert(DmsOrderBalanceAllocation allocation);

    int updatePendingAmount(@Param("id") Long id,
                            @Param("currentAmount") BigDecimal currentAmount,
                            @Param("status") Integer status);

    int markSettled(@Param("id") Long id,
                    @Param("settledAmount") BigDecimal settledAmount,
                    @Param("settleTime") LocalDateTime settleTime,
                    @Param("status") Integer status);

    int updateAfterReversal(@Param("id") Long id,
                            @Param("currentAmount") BigDecimal currentAmount,
                            @Param("reversedAmount") BigDecimal reversedAmount,
                            @Param("status") Integer status);

    int countPendingByTargetAgentId(@Param("targetAgentId") Long targetAgentId);
}
