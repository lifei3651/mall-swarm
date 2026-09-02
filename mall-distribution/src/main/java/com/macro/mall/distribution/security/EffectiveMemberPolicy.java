package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import com.macro.mall.distribution.vo.AgentInfoVO;

/** 团队后台与邀请能力共用的有效会员定义。 */
public final class EffectiveMemberPolicy {

    public static final String ACCESS_DENIED_MESSAGE = "当前账号暂未开通会员服务，如有疑问请联系客服核对会员资格。";

    private EffectiveMemberPolicy() {
    }

    public static boolean isActive(DmsShopMember member, DmsAgent agent) {
        return activeAccount(member) && agent != null && Integer.valueOf(1).equals(agent.getStatus())
                && AgentLevelEnum.getByValue(agent.getAgentLevel()) != null;
    }

    public static boolean isActive(DmsShopMember member, AgentInfoVO agent) {
        return activeAccount(member) && agent != null && Integer.valueOf(1).equals(agent.getStatus())
                && AgentLevelEnum.getByValue(agent.getAgentLevel()) != null;
    }

    public static void require(DmsShopMember member, AgentInfoVO agent) {
        if (!isActive(member, agent)) Asserts.fail(ACCESS_DENIED_MESSAGE);
    }

    private static boolean activeAccount(DmsShopMember member) {
        return member != null && Integer.valueOf(1).equals(member.getStatus())
                && !Integer.valueOf(1).equals(member.getSystemAccount());
    }
}
