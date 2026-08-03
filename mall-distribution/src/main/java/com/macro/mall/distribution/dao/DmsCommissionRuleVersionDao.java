package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsCommissionRuleVersionDao {

    List<DmsCommissionRuleVersion> selectByTenantId(@Param("tenantId") Long tenantId);

    DmsCommissionRuleVersion selectActiveByTenantId(@Param("tenantId") Long tenantId);

    int insert(DmsCommissionRuleVersion version);

}
