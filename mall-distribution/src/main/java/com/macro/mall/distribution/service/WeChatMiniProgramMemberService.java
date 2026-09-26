package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.security.EffectiveMemberPolicy;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Authenticated member's own capabilities and display rank only; never a team profile. */
@Service
@RequiredArgsConstructor
public class WeChatMiniProgramMemberService {
    private final DmsAgentDao agentDao;
    private final DmsTenantDao tenantDao;

    public Capabilities capabilities(DmsShopMember member) {
        // Read only the member's own qualification, without computing their team tree.
        var agent = member == null ? null : agentDao.selectByUserId(member.getUserId());
        boolean active = EffectiveMemberPolicy.isActive(member, agent);
        var tenant = tenantDao.selectById(TenantContext.getTenantId());
        boolean invitationEnabled = tenant != null && !Integer.valueOf(0).equals(tenant.getInvitationEnabled());
        String code = null;
        if (active && invitationEnabled) {
            String invite = agent.getInviteCode() == null || agent.getInviteCode().isBlank()
                    ? member.getInviteCode() : agent.getInviteCode();
            if (invite != null && invite.matches("[A-Za-z0-9]{8}")) {
                code = invite.toUpperCase(java.util.Locale.ROOT);
            }
        }
        // Wallet ownership is independent of invitation/promotion eligibility.
        boolean accountActive = member != null && Integer.valueOf(1).equals(member.getStatus())
                && !Integer.valueOf(1).equals(member.getSystemAccount());
        var level = active ? AgentLevelEnum.getByValue(agent.getAgentLevel()) : null;
        return new Capabilities(active, code != null, code, accountActive, accountActive,
                level == null ? null : level.getValue(), level == null ? "购物账号" : level.getName());
    }

    public record Capabilities(boolean membershipActive, boolean canInvite, String inviteCode,
                               boolean canViewWallet, boolean canViewPayoutRecords,
                               Integer membershipLevel, String membershipLabel) {}
}
