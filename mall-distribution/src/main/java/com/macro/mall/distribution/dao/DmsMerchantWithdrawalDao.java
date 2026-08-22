package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsMerchantWithdrawalDao {
    DmsMerchantWithdrawal selectById(@Param("id") Long id);
    DmsMerchantWithdrawal selectByIdForUpdate(@Param("id") Long id);
    DmsMerchantWithdrawal selectByRequestNo(@Param("tenantId") Long tenantId,
                                             @Param("merchantId") Long merchantId,
                                             @Param("requestNo") String requestNo);
    List<DmsMerchantWithdrawal> selectList(@Param("tenantId") Long tenantId,
                                           @Param("merchantId") Long merchantId,
                                           @Param("status") String status);
    int insert(DmsMerchantWithdrawal withdrawal);
    int update(DmsMerchantWithdrawal withdrawal);
    List<DmsMerchantWithdrawal> selectSensitivePlaintextCandidates(@Param("limit") int limit);
    int encryptSensitiveFields(@Param("id") Long id,
                               @Param("bankAccountNoSnapshot") String bankAccountNoSnapshot);
}
