package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsTenantDao {

    DmsTenant selectById(@Param("id") Long id);

    DmsTenant selectByCode(@Param("tenantCode") String tenantCode);

    List<DmsTenant> selectAll();

    int insert(DmsTenant tenant);

    int update(DmsTenant tenant);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
