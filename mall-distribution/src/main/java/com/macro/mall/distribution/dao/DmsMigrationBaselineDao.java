package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMigrationBaseline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsMigrationBaselineDao {
    DmsMigrationBaseline selectByAgentId(@Param("agentId") Long agentId);
    DmsMigrationBaseline selectByExternalCode(@Param("externalMemberCode") String externalMemberCode);
    int insert(DmsMigrationBaseline baseline);
}
