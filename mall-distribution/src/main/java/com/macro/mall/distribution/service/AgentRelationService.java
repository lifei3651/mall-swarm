package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsAgentRelation;

import java.util.List;

/**
 * 代理关系服务接口
 */
public interface AgentRelationService {

    /**
     * 绑定代理关系（建立上下级关系）
     * @param userId 用户ID
     * @param agentId 代理ID
     * @param parentUserId 上级用户ID
     * @param parentAgentId 上级代理ID
     * @param bindType 绑定方式
     * @return 是否成功
     */
    boolean bindRelation(Long userId, Long agentId, Long parentUserId, Long parentAgentId, Integer bindType);

    /**
     * 解绑代理关系（切线时使用）
     * @param userId 用户ID
     * @param parentUserId 上级用户ID
     * @param unbindReason 解绑原因
     * @return 是否成功
     */
    boolean unbindRelation(Long userId, Long parentUserId, String unbindReason);

    /**
     * 查询用户的有效上级关系
     * @param userId 用户ID
     * @return 上级关系列表
     */
    List<DmsAgentRelation> getValidRelationsByUserId(Long userId);

    /**
     * 查询代理的直属下级关系
     * @param parentAgentId 上级代理ID
     * @return 直属下级关系列表
     */
    List<DmsAgentRelation> getDirectChildren(Long parentAgentId);

    /**
     * 查询代理的所有下级关系（包括多级）
     * @param parentAgentId 上级代理ID
     * @return 所有下级关系列表
     */
    List<DmsAgentRelation> getAllDescendants(Long parentAgentId);

    /**
     * 查询代理的团队人数
     * @param agentId 代理ID
     * @return 团队人数
     */
    int getTeamMemberCount(Long agentId);

    /**
     * 查询代理的各层级人数
     * @param agentId 代理ID
     * @return 各层级人数数组 [一级人数, 二级人数, 三级人数]
     */
    int[] getLevelMemberCounts(Long agentId);
}
