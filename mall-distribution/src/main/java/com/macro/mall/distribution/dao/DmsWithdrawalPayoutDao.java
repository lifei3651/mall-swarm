package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWithdrawalPayout;
import com.macro.mall.distribution.vo.BusinessTimeoutMetricSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DmsWithdrawalPayoutDao {
    DmsWithdrawalPayout selectByWithdrawId(@Param("withdrawId") Long withdrawId);
    DmsWithdrawalPayout selectByWithdrawIdForUpdate(@Param("withdrawId") Long withdrawId);
    BusinessTimeoutMetricSnapshot selectTimedOutMetrics(@Param("cutoff") LocalDateTime cutoff);
    int insert(DmsWithdrawalPayout payout);
    int update(DmsWithdrawalPayout payout);
}
