package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAgentPerformanceSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 代理业绩汇总Mapper接口
 */
@Mapper
public interface DmsAgentPerformanceSummaryDao {

    /**
     * 根据ID查询汇总
     */
    DmsAgentPerformanceSummary selectById(@Param("id") Long id);

    /**
     * 查询代理在指定日期和统计类型的汇总
     */
    DmsAgentPerformanceSummary selectByAgentAndDate(@Param("agentId") Long agentId, @Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 查询代理的汇总列表
     */
    List<DmsAgentPerformanceSummary> selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 查询代理在日期范围内的汇总
     */
    List<DmsAgentPerformanceSummary> selectByAgentIdAndDateRange(@Param("agentId") Long agentId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("statType") Integer statType);

    /**
     * 查询指定日期的所有代理汇总
     */
    List<DmsAgentPerformanceSummary> selectByStatDate(@Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 插入汇总
     */
    int insert(DmsAgentPerformanceSummary summary);

    /**
     * 更新汇总
     */
    int update(DmsAgentPerformanceSummary summary);

    /**
     * 删除汇总
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除代理在指定日期的汇总
     */
    int deleteByAgentAndDate(@Param("agentId") Long agentId, @Param("statDate") LocalDate statDate, @Param("statType") Integer statType);

    /**
     * 删除代理的所有历史汇总（切线时使用）
     */
    int deleteAllByAgentId(@Param("agentId") Long agentId);
}
