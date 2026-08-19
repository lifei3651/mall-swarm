package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsAgentAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.AgentAccountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 代理账户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAccountServiceImpl implements AgentAccountService {

    private final DmsAgentAccountDao accountDao;
    private final DmsShopMemberDao memberDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean initAccount(Long agentId, Long userId) {
        // 检查账户是否已存在
        DmsAgentAccount existAccount = accountDao.selectByAgentId(agentId);
        if (existAccount != null) {
            log.warn("代理账户已存在: agentId={}", agentId);
            return true;
        }

        DmsAgentAccount account = new DmsAgentAccount();
        account.setAgentId(agentId);
        account.setUserId(userId);
        account.setTotalCommission(BigDecimal.ZERO);
        account.setSettledCommission(BigDecimal.ZERO);
        account.setUnsettledCommission(BigDecimal.ZERO);
        account.setFrozenCommission(BigDecimal.ZERO);
        account.setWithdrawnAmount(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setTotalOrders(0);
        account.setTotalTeamMembers(0);

        int result = accountDao.insert(account);
        log.info("初始化代理账户: agentId={}, userId={}, result={}", agentId, userId, result);
        return result > 0;
    }

    @Override
    public AgentAccountVO getAccountByAgentId(Long agentId) {
        DmsAgentAccount account = accountDao.selectByAgentId(agentId);
        return account != null ? convertToVO(account) : null;
    }

    @Override
    public AgentAccountVO getAccountByUserId(Long userId) {
        DmsAgentAccount account = accountDao.selectByUserId(userId);
        return account != null ? convertToVO(account) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCommission(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        // 增加累计佣金
        accountDao.addTotalCommission(agentId, amount);
        // 增加待结算佣金
        accountDao.addUnsettledCommission(agentId, amount);

        log.info("增加佣金: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean subtractUnsettledCommission(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        DmsAgentAccount account = accountDao.selectByAgentId(agentId);
        if (account == null) {
            Asserts.fail("代理账户不存在");
        }
        if (account.getUnsettledCommission().compareTo(amount) < 0) {
            Asserts.fail("待结算佣金不足");
        }
        if (accountDao.subtractUnsettledCommission(agentId, amount) != 1) {
            Asserts.fail("待结算佣金不足");
        }
        log.info("减少待结算佣金: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleCommission(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        // 减少待结算佣金
        if (accountDao.subtractUnsettledCommission(agentId, amount) != 1) {
            Asserts.fail("待结算佣金不足");
        }
        // 增加已结算佣金
        accountDao.addSettledCommission(agentId, amount);

        log.info("结算佣金: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addAvailableBalance(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }

        accountDao.addAvailableBalance(agentId, amount);
        log.info("增加可提现余额: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addWithdrawnAmount(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        int updated = accountDao.addWithdrawnAmount(agentId, amount);
        if (updated <= 0) {
            Asserts.fail("代理账户不存在");
        }
        log.info("增加已提现金额: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean withdraw(Long agentId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("提现金额必须大于0");
        }

        // 检查可提现余额
        DmsAgentAccount account = accountDao.selectByAgentId(agentId);
        if (account == null) {
            Asserts.fail("代理账户不存在");
        }
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            Asserts.fail("可提现余额不足");
        }

        int updated = accountDao.subtractAvailableBalance(agentId, amount);
        if (updated <= 0) {
            Asserts.fail("可提现余额不足");
        }

        log.info("锁定提现余额: agentId={}, amount={}", agentId, amount);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addOrderCount(Long agentId, Integer count) {
        if (count <= 0) {
            return true;
        }

        accountDao.addTotalOrders(agentId, count);
        log.info("增加订单数: agentId={}, count={}", agentId, count);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTeamMemberCount(Long agentId, Integer count) {
        accountDao.updateTotalTeamMembers(agentId, count);
        log.info("更新团队成员数: agentId={}, count={}", agentId, count);
        return true;
    }

    /**
     * 转换为VO
     */
    private AgentAccountVO convertToVO(DmsAgentAccount account) {
        AgentAccountVO vo = new AgentAccountVO();
        BeanUtils.copyProperties(account, vo);
        DmsShopMember member = memberDao.selectByUserId(account.getUserId());
        vo.setMemberAccount(MemberAccountUtils.display(member));
        return vo;
    }
}
