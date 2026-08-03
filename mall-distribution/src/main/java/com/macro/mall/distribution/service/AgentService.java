package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.vo.AgentInfoVO;

import java.util.List;

/**
 * 代理服务接口
 */
public interface AgentService {

    /**
     * 代理注册
     * @param registerDTO 注册信息
     * @return 代理信息
     */
    AgentInfoVO register(AgentRegisterDTO registerDTO);

    /**
     * 根据ID查询代理
     * @param id 代理ID
     * @return 代理信息
     */
    AgentInfoVO getAgentById(Long id);

    /**
     * 根据用户ID查询代理
     * @param userId 用户ID
     * @return 代理信息
     */
    AgentInfoVO getAgentByUserId(Long userId);

    /**
     * 根据代理编号查询代理
     * @param agentCode 代理编号
     * @return 代理信息
     */
    AgentInfoVO getAgentByAgentCode(String agentCode);

    /**
     * 根据邀请码查询代理
     * @param inviteCode 邀请码
     * @return 代理信息
     */
    AgentInfoVO getAgentByInviteCode(String inviteCode);

    /**
     * 查询代理列表
     * @param keyword 关键词
     * @param status 状态
     * @return 代理列表
     */
    List<AgentInfoVO> listAgents(String keyword, Integer status);

    /** 按级别组合筛选代理，分页必须在数据库筛选之后生效。 */
    List<AgentInfoVO> listAgents(String keyword, Integer status, Integer agentLevel);

    /** 查询所有根代理；手工设级和首单激活产生的无上级代理都应返回。 */
    List<AgentInfoVO> getRootAgents();

    /**
     * 查询代理的下级代理列表
     * @param parentId 上级代理ID
     * @return 下级代理列表
     */
    List<AgentInfoVO> getChildrenAgents(Long parentId);

    /**
     * 查询代理的所有下级代理（包括多级）
     * @param agentId 代理ID
     * @return 所有下级代理列表
     */
    List<AgentInfoVO> getAllDescendants(Long agentId);

    /**
     * 代理切线（变更上级关系）
     * @param switchLineDTO 切线信息
     * @return 是否成功
     */
    boolean switchLine(AgentSwitchLineDTO switchLineDTO);

    /**
     * 更新代理信息
     * @param agent 代理信息
     * @return 是否成功
     */
    boolean updateAgent(DmsAgent agent);

    /**
     * 更新代理信息（安全版本，仅允许更新指定字段）
     * @param id 代理ID
     * @param updateDTO 更新信息
     * @return 是否成功
     */
    boolean updateAgentInfo(Long id, AgentUpdateDTO updateDTO);

    /**
     * 更新代理状态
     * @param id 代理ID
     * @param status 状态
     * @return 是否成功
     */
    boolean updateStatus(Long id, Integer status);

    /** 管理员/系统直接调级；历史奖金不重算，新等级只影响生效后的订单。 */
    AgentInfoVO adjustLevel(Long id, Integer level, String reason);

    /**
     * 生成邀请码
     * @return 邀请码
     */
    String generateInviteCode();

    /**
     * 生成推广二维码URL
     * @param agentId 代理ID
     * @return 二维码URL
     */
    String generateQrCodeUrl(Long agentId);
}
