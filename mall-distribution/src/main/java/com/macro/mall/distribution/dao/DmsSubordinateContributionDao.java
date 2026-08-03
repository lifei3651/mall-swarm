package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsSubordinateContribution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 下属业绩贡献Mapper接口
 */
@Mapper
public interface DmsSubordinateContributionDao {

    /**
     * 根据ID查询贡献记录
     */
    DmsSubordinateContribution selectById(@Param("id") Long id);

    /**
     * 查询代理在指定日期和统计类型的所有下属贡献
     */
    List<DmsSubordinateContribution> selectByAgentAndDate(@Param("agentId") Long agentId, @Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 查询代理在日期范围内的下属贡献
     */
    List<DmsSubordinateContribution> selectByAgentAndDateRange(@Param("agentId") Long agentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("statType") Integer statType);

    /**
     * 查询特定下属对代理的贡献
     */
    DmsSubordinateContribution selectByAgentAndSubordinate(@Param("agentId") Long agentId, @Param("subordinateAgentId") Long subordinateAgentId, @Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 插入贡献记录
     */
    int insert(DmsSubordinateContribution contribution);

    /**
     * 更新贡献记录
     */
    int update(DmsSubordinateContribution contribution);

    /**
     * 删除贡献记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除代理在指定日期的所有贡献记录
     */
    int deleteByAgentAndDate(@Param("agentId") Long agentId, @Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 删除代理的所有历史贡献记录（切线时使用）
     */
    int deleteAllByAgentId(@Param("agentId") Long agentId);
}
