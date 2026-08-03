package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAgentAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 代理账户Mapper接口
 */
@Mapper
public interface DmsAgentAccountDao {

    /**
     * 根据ID查询账户
     */
    DmsAgentAccount selectById(@Param("id") Long id);

    /**
     * 根据代理ID查询账户
     */
    DmsAgentAccount selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据用户ID查询账户
     */
    DmsAgentAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 插入账户
     */
    int insert(DmsAgentAccount account);

    /**
     * 更新账户
     */
    int update(DmsAgentAccount account);

    /**
     * 增加累计佣金
     */
    int addTotalCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 减少累计佣金
     */
    int subtractTotalCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 增加待结算佣金
     */
    int addUnsettledCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 减少待结算佣金
     */
    int subtractUnsettledCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 增加已结算佣金
     */
    int addSettledCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 减少已结算佣金
     */
    int subtractSettledCommission(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 增加可提现余额
     */
    int addAvailableBalance(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 减少可提现余额
     */
    int subtractAvailableBalance(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 增加已提现金额
     */
    int addWithdrawnAmount(@Param("agentId") Long agentId, @Param("amount") BigDecimal amount);

    /**
     * 增加订单数
     */
    int addTotalOrders(@Param("agentId") Long agentId, @Param("count") Integer count);

    /**
     * 更新团队成员数
     */
    int updateTotalTeamMembers(@Param("agentId") Long agentId, @Param("count") Integer count);

    /**
     * 删除账户
     */
    int deleteById(@Param("id") Long id);

    int deleteByAgentId(@Param("agentId") Long agentId);
}
