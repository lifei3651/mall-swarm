package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.security.EffectiveMemberPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Public native capabilities, not a team/earnings profile. No rank, relations or bonus amounts. */
@Service
@RequiredArgsConstructor
public class WeChatMiniProgramMemberService {
    private final DmsAgentDao agentDao;

    public Capabilities capabilities(DmsShopMember member) {
        // Read only the member's own qualification, without computing their team tree.
        var agent = member == null ? null : agentDao.selectByUserId(member.getUserId());
        boolean active = EffectiveMemberPolicy.isActive(member, agent);
        String code = null;
        if (active) {
            String invite = agent.getInviteCode() == null || agent.getInviteCode().isBlank()
                    ? member.getInviteCode() : agent.getInviteCode();
            if (invite != null && invite.matches("[A-Za-z0-9]{8}")) {
                code = invite.toUpperCase(java.util.Locale.ROOT);
            }
        }
        // Wallet ownership is independent of invitation/promotion eligibility.
        boolean accountActive = member != null && Integer.valueOf(1).equals(member.getStatus())
                && !Integer.valueOf(1).equals(member.getSystemAccount());
        return new Capabilities(active, code != null, code, accountActive, accountActive);
    }

    public record Capabilities(boolean membershipActive, boolean canInvite, String inviteCode,
                               boolean canViewWallet, boolean canViewPayoutRecords) {}
}
