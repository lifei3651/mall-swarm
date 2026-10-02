package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.AgentService;
import java.util.Locale;

final class InvitationMemberPolicy {
    private InvitationMemberPolicy() {}
    static DmsShopMember resolve(DmsShopMemberDao members, AgentService agents, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{8}")) Asserts.fail("邀请码无效");
        DmsShopMember inviter = members.selectByInviteCode(normalized);
        if (inviter == null) {
            var legacy = agents.getAgentByInviteCode(normalized);
            if (legacy != null && Integer.valueOf(1).equals(legacy.getStatus()))
                inviter = members.selectByUserId(legacy.getUserId());
        }
        if (inviter == null || !Integer.valueOf(1).equals(inviter.getStatus())
                || Integer.valueOf(1).equals(inviter.getSystemAccount())) Asserts.fail("邀请码无效");
        return inviter;
    }
}
