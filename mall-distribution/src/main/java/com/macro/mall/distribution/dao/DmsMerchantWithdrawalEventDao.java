package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantWithdrawalEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsMerchantWithdrawalEventDao {
    int insert(DmsMerchantWithdrawalEvent event);
    List<DmsMerchantWithdrawalEvent> selectByWithdrawalId(@Param("withdrawalId") Long withdrawalId);
}
