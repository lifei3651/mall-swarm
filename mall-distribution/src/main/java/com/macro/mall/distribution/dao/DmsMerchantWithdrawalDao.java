package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsMerchantWithdrawalDao {
    DmsMerchantWithdrawal selectById(@Param("id") Long id);
    DmsMerchantWithdrawal selectByIdForUpdate(@Param("id") Long id);
    List<DmsMerchantWithdrawal> selectList(@Param("tenantId") Long tenantId,
                                           @Param("merchantId") Long merchantId,
                                           @Param("status") String status);
    int insert(DmsMerchantWithdrawal withdrawal);
    int update(DmsMerchantWithdrawal withdrawal);
}
