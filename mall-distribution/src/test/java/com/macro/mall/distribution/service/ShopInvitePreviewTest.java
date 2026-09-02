package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopInvitePreviewTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsAgentDao agentDao;
    @InjectMocks private ShopServiceImpl shopService;

    @Test
    void previewNormalizesCodeAndReturnsOnlyPublicNickname() {
        DmsShopMember inviter = new DmsShopMember();
        inviter.setNickname("邀请会员甲");
        inviter.setPhone("13900001234");
        inviter.setUserId(99887766L);
        inviter.setStatus(1);
        DmsAgent agent = activeAgent(99887766L);
        when(memberDao.selectByInviteCode("ABCD1234")).thenReturn(inviter);
        when(agentDao.selectByUserId(99887766L)).thenReturn(agent);

        Map<String, Object> preview = shopService.getInviterPreview(" abcd1234 ");

        assertEquals(true, preview.get("valid"));
        assertEquals("邀请会员甲", preview.get("nickname"));
        assertEquals(2, preview.size(), "注册页只能返回校验状态和邀请人昵称");
    }

    @Test
    void legacyAgentInviteCodeStillResolvesToShopMember() {
        DmsAgent legacyAgent = new DmsAgent();
        legacyAgent.setUserId(99887766L);
        legacyAgent.setStatus(1);
        legacyAgent.setAgentLevel(1);
        DmsShopMember inviter = new DmsShopMember();
        inviter.setUserId(99887766L);
        inviter.setNickname("历史会员乙");
        inviter.setStatus(1);
        when(memberDao.selectByInviteCode("OLDLINK1")).thenReturn(null);
        when(agentDao.selectByInviteCode("OLDLINK1")).thenReturn(legacyAgent);
        when(memberDao.selectByUserId(99887766L)).thenReturn(inviter);
        when(agentDao.selectByUserId(99887766L)).thenReturn(legacyAgent);

        Map<String, Object> preview = shopService.getInviterPreview("oldlink1");

        assertEquals(true, preview.get("valid"));
        assertEquals("历史会员乙", preview.get("nickname"));
        assertEquals(2, preview.size());
    }

    @Test
    void unknownCodeReturnsNormalValidationResult() {
        when(memberDao.selectByInviteCode("INVALID1")).thenReturn(null);
        when(agentDao.selectByInviteCode("INVALID1")).thenReturn(null);

        Map<String, Object> preview = shopService.getInviterPreview("invalid1");

        assertEquals(false, preview.get("valid"));
        assertEquals("未找到该邀请码，请向邀请人核对", preview.get("message"));
        assertEquals(2, preview.size());
    }

    @Test
    void missingNicknameNeverFallsBackToLoginAccount() {
        DmsShopMember inviter = new DmsShopMember();
        inviter.setUsername("private_login_account");
        inviter.setUserId(99887766L);
        inviter.setStatus(1);
        when(memberDao.selectByInviteCode("NICKLESS")).thenReturn(inviter);
        when(agentDao.selectByUserId(99887766L)).thenReturn(activeAgent(99887766L));

        Map<String, Object> preview = shopService.getInviterPreview("nickless");

        assertEquals(true, preview.get("valid"));
        assertEquals("商城会员", preview.get("nickname"));
        assertEquals(2, preview.size());
    }

    private DmsAgent activeAgent(Long userId) {
        DmsAgent agent = new DmsAgent();
        agent.setUserId(userId);
        agent.setStatus(1);
        agent.setAgentLevel(1);
        return agent;
    }
}
