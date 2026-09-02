package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.macro.mall.distribution.dto.MerchantControlDTO;
import com.macro.mall.distribution.vo.MerchantExitReadinessVO;

@Mapper
public interface DmsMerchantDao {
    DmsMerchant selectById(@Param("id") Long id);
    DmsMerchant selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
    DmsMerchant selectByNo(@Param("tenantId") Long tenantId, @Param("merchantNo") String merchantNo);
    Integer selectDefaultSettlementDays(@Param("id") Long id);
    List<DmsMerchant> selectList(@Param("tenantId") Long tenantId, @Param("keyword") String keyword,
                                 @Param("status") Integer status);
    int insert(DmsMerchant merchant);
    int update(DmsMerchant merchant);
    int submitProfile(@Param("merchant") DmsMerchant merchant);
    List<DmsMerchant> selectSensitivePlaintextCandidates(@Param("limit") int limit);
    int encryptSensitiveFields(@Param("id") Long id, @Param("bankAccountNo") String bankAccountNo);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int updateControls(@Param("id") Long id, @Param("status") Integer status,
                       @Param("control") MerchantControlDTO control);
    MerchantExitReadinessVO selectExitReadiness(@Param("tenantId") Long tenantId,
                                                @Param("merchantId") Long merchantId);
}
