package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAgentChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 代理变更日志Mapper接口
 */
@Mapper
public interface DmsAgentChangeLogDao {

    /**
     * 根据ID查询日志
     */
    DmsAgentChangeLog selectById(@Param("id") Long id);

    /**
     * 根据代理ID查询日志
     */
    List<DmsAgentChangeLog> selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据代理ID和变更类型查询日志
     */
    List<DmsAgentChangeLog> selectByAgentIdAndChangeType(@Param("agentId") Long agentId, @Param("changeType") Integer changeType);

    /**
     * 插入日志
     */
    int insert(DmsAgentChangeLog log);

    /**
     * 删除日志
     */
    int deleteById(@Param("id") Long id);
}
