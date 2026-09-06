package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.dao.DmsAgentDao;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeChatMiniProgramMemberServiceTest {
    private final DmsAgentDao agents = mock(DmsAgentDao.class);
    private final WeChatMiniProgramMemberService service = new WeChatMiniProgramMemberService(agents);
    private DmsShopMember member() {
        DmsShopMember m = new DmsShopMember(); m.setUserId(100L); m.setStatus(1); m.setSystemAccount(0); return m;
    }
    private DmsAgent agent(int level, int status) {
        DmsAgent a = new DmsAgent(); a.setAgentLevel(level); a.setStatus(status); return a;
    }
    @Test void ordinaryAccountKeepsOwnWalletButNeverGainsInvitationFromBalanceOrRelation() {
        var m = member(); m.setInviterId(200L); m.setTeamOptIn(1);
        var result = service.capabilities(m);
        assertTrue(result.canViewWallet()); assertTrue(result.canViewPayoutRecords());
        assertFalse(result.membershipActive()); assertFalse(result.canInvite()); assertNull(result.inviteCode());
    }
    @Test void activeMemberUsesCanonicalHistoricalInviteCodeWithoutLeakingTeamDetails() {
        var m = member(); m.setInviteCode("NEWX1234"); var a = agent(1, 1); a.setInviteCode("abcd1234");
        when(agents.selectByUserId(100L)).thenReturn(a);
        var result = service.capabilities(m);
        assertTrue(result.membershipActive()); assertTrue(result.canInvite()); assertEquals("ABCD1234", result.inviteCode());
        assertEquals(7, result.getClass().getRecordComponents().length);
        assertEquals(1, result.membershipLevel()); assertEquals("会员", result.membershipLabel());
    }
    @Test void invalidRankDisabledAndSystemAccountsCannotInvite() {
        var m = member(); when(agents.selectByUserId(100L)).thenReturn(agent(999, 1));
        assertFalse(service.capabilities(m).canInvite());
        when(agents.selectByUserId(100L)).thenReturn(agent(1, 0));
        assertFalse(service.capabilities(m).canInvite());
        m.setSystemAccount(1); when(agents.selectByUserId(100L)).thenReturn(agent(1, 1));
        assertFalse(service.capabilities(m).canViewWallet());
    }
    @Test void malformedCodeDoesNotBecomeSharePermission() {
        var m = member(); when(agents.selectByUserId(100L)).thenReturn(agent(1, 1));
        m.setInviteCode("ABCD1234&other=1");
        assertFalse(service.capabilities(m).canInvite());
    }
}
