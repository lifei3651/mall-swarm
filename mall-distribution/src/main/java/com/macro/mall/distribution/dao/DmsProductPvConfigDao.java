package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsProductPvConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsProductPvConfigDao {

    DmsProductPvConfig selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default DmsProductPvConfig selectById(Long id) {
        return selectByIdScoped(TenantContext.getTenantId(), id);
    }

    List<DmsProductPvConfig> selectList(@Param("tenantId") Long tenantId,
                                        @Param("keyword") String keyword,
                                        @Param("status") Integer status);

    int insert(DmsProductPvConfig config);

    int updateScoped(@Param("tenantId") Long tenantId, @Param("config") DmsProductPvConfig config);

    default int update(DmsProductPvConfig config) {
        return updateScoped(TenantContext.getTenantId(), config);
    }

    int updateStatusScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);

    default int updateStatus(Long id, Integer status) {
        return updateStatusScoped(TenantContext.getTenantId(), id, status);
    }

    int deleteByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);

    default int deleteById(Long id) {
        return deleteByIdScoped(TenantContext.getTenantId(), id);
    }
}
