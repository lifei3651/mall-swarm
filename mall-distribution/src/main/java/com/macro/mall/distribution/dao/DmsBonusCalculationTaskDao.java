package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsBonusCalculationTaskDao {

    DmsBonusCalculationTask selectById(@Param("id") Long id);

    DmsBonusCalculationTask selectLatestByOrderId(@Param("orderId") Long orderId);

    List<DmsBonusCalculationTask> selectList(@Param("status") Integer status,
                                             @Param("orderId") Long orderId);

    List<DmsBonusCalculationTask> selectExecutable(@Param("limit") Integer limit);

    int insert(DmsBonusCalculationTask task);

    int markProcessing(@Param("id") Long id);

    int markSuccess(@Param("id") Long id);

    int markFailed(@Param("id") Long id,
                   @Param("failReason") String failReason,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
