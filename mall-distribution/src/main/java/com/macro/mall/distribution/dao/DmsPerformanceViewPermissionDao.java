package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsPerformanceViewPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsPerformanceViewPermissionDao {

    DmsPerformanceViewPermission selectById(@Param("id") Long id);

    DmsPerformanceViewPermission selectByAgentId(@Param("agentId") Long agentId);

    DmsPerformanceViewPermission selectByUserId(@Param("userId") Long userId);

    List<DmsPerformanceViewPermission> selectAll();

    int insert(DmsPerformanceViewPermission permission);

    int update(DmsPerformanceViewPermission permission);

    int deleteById(@Param("id") Long id);
}
