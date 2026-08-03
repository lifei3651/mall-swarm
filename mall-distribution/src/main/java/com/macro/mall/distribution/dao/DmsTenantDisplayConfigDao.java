package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsTenantDisplayConfigDao {

    DmsTenantDisplayConfig selectByTenantId(@Param("tenantId") Long tenantId);

    int insert(DmsTenantDisplayConfig config);

    int update(DmsTenantDisplayConfig config);
}
