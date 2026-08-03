package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.AgentAccountVO;

import java.math.BigDecimal;

/**
 * 代理账户服务接口
 */
public interface AgentAccountService {

    /**
     * 初始化代理账户
     * @param agentId 代理ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean initAccount(Long agentId, Long userId);

    /**
     * 查询代理账户信息
     * @param agentId 代理ID
     * @return 账户信息
     */
    AgentAccountVO getAccountByAgentId(Long agentId);

    /**
     * 查询用户账户信息
     * @param userId 用户ID
     * @return 账户信息
     */
    AgentAccountVO getAccountByUserId(Long userId);

    /**
     * 增加佣金
     * @param agentId 代理ID
     * @param amount 佣金金额
     * @return 是否成功
     */
    boolean addCommission(Long agentId, BigDecimal amount);

    /**
     * 减少待结算佣金
     * @param agentId 代理ID
     * @param amount 佣金金额
     * @return 是否成功
     */
    boolean subtractUnsettledCommission(Long agentId, BigDecimal amount);

    /**
     * 结算佣金（从待结算转为已结算）
     * @param agentId 代理ID
     * @param amount 佣金金额
     * @return 是否成功
     */
    boolean settleCommission(Long agentId, BigDecimal amount);

    /**
     * 增加可提现余额
     * @param agentId 代理ID
     * @param amount 金额
     * @return 是否成功
     */
    boolean addAvailableBalance(Long agentId, BigDecimal amount);

    /**
     * 增加已提现金额
     * @param agentId 代理ID
     * @param amount 金额
     * @return 是否成功
     */
    boolean addWithdrawnAmount(Long agentId, BigDecimal amount);

    /**
     * 提现（减少可提现余额，增加已提现金额）
     * @param agentId 代理ID
     * @param amount 提现金额
     * @return 是否成功
     */
    boolean withdraw(Long agentId, BigDecimal amount);

    /**
     * 增加订单数
     * @param agentId 代理ID
     * @param count 订单数
     * @return 是否成功
     */
    boolean addOrderCount(Long agentId, Integer count);

    /**
     * 更新团队成员数
     * @param agentId 代理ID
     * @param count 成员数
     * @return 是否成功
     */
    boolean updateTeamMemberCount(Long agentId, Integer count);
}
