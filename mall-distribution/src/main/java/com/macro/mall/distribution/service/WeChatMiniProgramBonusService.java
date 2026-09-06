package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Own-money projection only. Reuses tenant-scoped settlement queries, never a team/order projection. */
@Service
@RequiredArgsConstructor
public class WeChatMiniProgramBonusService {
    private final DmsAgentDao agentDao;
    private final DmsCommissionRecordDao recordDao;

    @Transactional(readOnly = true)
    public Summary summary(DmsShopMember member) {
        if (member == null || member.getUserId() == null || !Integer.valueOf(1).equals(member.getStatus())
                || Integer.valueOf(1).equals(member.getSystemAccount())) throw new IllegalArgumentException("请使用有效的本人商城账号");
        var agent = agentDao.selectByUserId(member.getUserId());
        if (agent == null) return new Summary(BigDecimal.ZERO, BigDecimal.ZERO);
        // Historical money remains visible even when invitation privileges are later disabled.
        return new Summary(recordDao.selectSettledAmountByAgentId(agent.getId()),
                recordDao.selectUnsettledAmountByAgentId(agent.getId()));
    }
    public record Summary(BigDecimal issuedBonus, BigDecimal pendingBonus) {}
}
