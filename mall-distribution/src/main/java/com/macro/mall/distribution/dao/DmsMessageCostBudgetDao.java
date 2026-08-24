package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DmsMessageCostBudgetDao {
    DmsMessageCostBudget selectForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("scopeType") String scopeType,
                                         @Param("scopeKey") String scopeKey);
    List<DmsMessageCostBudget> selectList(@Param("tenantId") Long tenantId);
    BigDecimal sumReserved(@Param("tenantId") Long tenantId,
                           @Param("scopeType") String scopeType,
                           @Param("scopeKey") String scopeKey,
                           @Param("fromTime") LocalDateTime fromTime);
}
