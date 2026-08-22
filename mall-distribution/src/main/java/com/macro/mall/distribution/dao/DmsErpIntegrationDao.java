package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsErpIntegration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsErpIntegrationDao {
    DmsErpIntegration selectById(@Param("id") Long id);
    DmsErpIntegration selectByTenantAndProvider(@Param("tenantId") Long tenantId, @Param("providerCode") String providerCode);
    List<DmsErpIntegration> selectEnabled(@Param("tenantId") Long tenantId);
    List<DmsErpIntegration> selectList(@Param("tenantId") Long tenantId);
    int insert(DmsErpIntegration entity);
    int update(DmsErpIntegration entity);
    List<DmsErpIntegration> selectSensitivePlaintextCandidates(@Param("limit") int limit);
    int encryptSensitiveFields(@Param("id") Long id,
                               @Param("appSecret") String appSecret,
                               @Param("callbackToken") String callbackToken);
}
