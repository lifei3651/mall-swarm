package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWithdrawalPayout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsWithdrawalPayoutDao {
    DmsWithdrawalPayout selectByWithdrawId(@Param("withdrawId") Long withdrawId);
    DmsWithdrawalPayout selectByWithdrawIdForUpdate(@Param("withdrawId") Long withdrawId);
    int insert(DmsWithdrawalPayout payout);
    int update(DmsWithdrawalPayout payout);
}
