package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAgentRelation;
import com.macro.mall.distribution.vo.AgentTeamMemberCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 代理关系Mapper接口
 */
@Mapper
public interface DmsAgentRelationDao {

    /**
     * 根据ID查询关系
     */
    DmsAgentRelation selectById(@Param("id") Long id);

    /**
     * 查询用户的有效上级关系
     */
    DmsAgentRelation selectValidRelation(@Param("userId") Long userId, @Param("parentUserId") Long parentUserId);

    /**
     * 查询用户的所有有效上级关系
     */
    List<DmsAgentRelation> selectValidRelationsByUserId(@Param("userId") Long userId);

    /**
     * 查询代理的所有有效下级关系
     */
    List<DmsAgentRelation> selectValidRelationsByParentAgentId(@Param("parentAgentId") Long parentAgentId);

    /**
     * 查询代理的直属下级关系
     */
    List<DmsAgentRelation> selectDirectChildren(@Param("parentAgentId") Long parentAgentId);

    /**
     * 查询代理的所有下级关系（包括多级）
     */
    List<DmsAgentRelation> selectAllDescendants(@Param("parentAgentId") Long parentAgentId);

    /**
     * 批量统计一组代理的有效团队人数，未产生下级关系的代理不会出现在结果中。
     */
    List<AgentTeamMemberCountVO> selectTeamMemberCounts(@Param("agentIds") List<Long> agentIds);

    /**
     * 根据关系路径查询
     */
    List<DmsAgentRelation> selectByRelationPath(@Param("relationPath") String relationPath);

    /**
     * 插入关系
     */
    int insert(DmsAgentRelation relation);

    /**
     * 更新关系
     */
    int update(DmsAgentRelation relation);

    /**
     * 使关系失效（切线时使用）
     */
    int invalidRelation(@Param("userId") Long userId, @Param("parentUserId") Long parentUserId, @Param("unbindReason") String unbindReason);

    /** 作废一组代理的全部有效上级关系（整棵团队移线时使用） */
    int invalidRelationsByAgentIds(@Param("agentIds") List<Long> agentIds,
                                   @Param("unbindReason") String unbindReason);

    /**
     * 删除关系
     */
    int deleteById(@Param("id") Long id);

    int deleteByAgentId(@Param("agentId") Long agentId);
}
