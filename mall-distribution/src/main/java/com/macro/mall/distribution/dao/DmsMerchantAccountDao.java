package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsMerchantAccountDao {
    DmsMerchantAccount selectByMerchantId(@Param("merchantId") Long merchantId);
    DmsMerchantAccount selectByMerchantIdForUpdate(@Param("merchantId") Long merchantId);
    List<DmsMerchantAccount> selectList(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);
    int insert(DmsMerchantAccount account);
    int addPending(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int releasePending(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int reversePending(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int reverseAvailableOrCreateDebt(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int freezeAvailable(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int unfreeze(@Param("merchantId") Long merchantId, @Param("amount") BigDecimal amount);
    int settleFrozen(@Param("merchantId") Long merchantId,
                     @Param("requestedAmount") BigDecimal requestedAmount,
                     @Param("actualPaidAmount") BigDecimal actualPaidAmount,
                     @Param("adjustmentAmount") BigDecimal adjustmentAmount);
}
