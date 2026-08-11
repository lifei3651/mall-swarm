package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsTenantConfigVersion;
import com.macro.mall.distribution.vo.TenantConfigVersionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsTenantConfigVersionDao {

    int insert(DmsTenantConfigVersion version);

    int countByTenantId(@Param("tenantId") Long tenantId);

    DmsTenantConfigVersion selectByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<TenantConfigVersionVO> selectMetadataByTenantId(@Param("tenantId") Long tenantId);
}
