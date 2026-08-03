package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsPerformanceRanking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 业绩排名Mapper接口
 */
@Mapper
public interface DmsPerformanceRankingDao {

    /**
     * 根据ID查询排名
     */
    DmsPerformanceRanking selectById(@Param("id") Long id);

    /**
     * 查询指定类型、周期、日期的排名列表
     */
    List<DmsPerformanceRanking> selectByTypeAndDate(@Param("rankType") Integer rankType, @Param("rankPeriod") Integer rankPeriod, @Param("statDate") LocalDate statDate);

    /**
     * 查询指定类型、周期、日期的前N名
     */
    List<DmsPerformanceRanking> selectTopN(@Param("rankType") Integer rankType, @Param("rankPeriod") Integer rankPeriod, @Param("statDate") LocalDate statDate, @Param("limit") Integer limit);

    /**
     * 查询代理在指定类型、周期、日期的排名
     */
    DmsPerformanceRanking selectByAgentAndType(@Param("agentId") Long agentId, @Param("rankType") Integer rankType, @Param("rankPeriod") Integer rankPeriod, @Param("statDate") LocalDate statDate);

    /**
     * 插入排名
     */
    int insert(DmsPerformanceRanking ranking);

    /**
     * 更新排名
     */
    int update(DmsPerformanceRanking ranking);

    /**
     * 删除排名
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除指定类型、周期、日期的所有排名
     */
    int deleteByTypeAndDate(@Param("rankType") Integer rankType, @Param("rankPeriod") Integer rankPeriod, @Param("statDate") LocalDate statDate);
}
