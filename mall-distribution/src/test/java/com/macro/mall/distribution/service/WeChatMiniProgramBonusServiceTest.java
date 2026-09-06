package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.controller.WeChatMiniProgramBonusController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeChatMiniProgramBonusServiceTest {
    private final DmsAgentDao agents = mock(DmsAgentDao.class);
    private final DmsCommissionRecordDao records = mock(DmsCommissionRecordDao.class);
    private final WeChatMiniProgramBonusService service = new WeChatMiniProgramBonusService(agents, records);
    private DmsShopMember member() { var m = new DmsShopMember(); m.setUserId(100L); m.setStatus(1); return m; }
    @Test void ownBonusUsesActualSettledAndPendingRecordsNotAccountBalanceOrTeam() {
        var agent = new DmsAgent(); agent.setId(42L); agent.setStatus(0);
        when(agents.selectByUserId(100L)).thenReturn(agent);
        when(records.selectSettledAmountByAgentId(42L)).thenReturn(new BigDecimal("61.12"));
        when(records.selectUnsettledAmountByAgentId(42L)).thenReturn(new BigDecimal("18.21"));
        var summary = service.summary(member());
        assertEquals(new BigDecimal("61.12"), summary.issuedBonus());
        assertEquals(new BigDecimal("18.21"), summary.pendingBonus());
        assertEquals(2, summary.getClass().getRecordComponents().length);
        verify(records).selectSettledAmountByAgentId(42L); verify(records).selectUnsettledAmountByAgentId(42L);
        verifyNoMoreInteractions(records);
    }
    @Test void ordinaryMemberHasZeroBonusWithoutReadingAnyOtherAgent() {
        assertEquals(BigDecimal.ZERO, service.summary(member()).issuedBonus()); verifyNoInteractions(records);
    }
    @Test void inactiveSystemAndMissingAccountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.summary(null));
        var member = member(); member.setStatus(0);
        assertThrows(IllegalArgumentException.class, () -> service.summary(member));
        member.setStatus(1); member.setSystemAccount(1);
        assertThrows(IllegalArgumentException.class, () -> service.summary(member)); verifyNoInteractions(agents, records);
    }
    @Test void controllerUsesSessionOwnerAndNoStoreNeverARequestedUserId() {
        var auth = mock(ShopAuthService.class); var controller = new WeChatMiniProgramBonusController(auth, service);
        when(auth.requireMember(null)).thenThrow(new IllegalArgumentException("login required"));
        assertThrows(IllegalArgumentException.class, () -> controller.summary(null, new MockHttpServletResponse()));
        verifyNoInteractions(agents, records);
        when(auth.requireMember("Bearer test-session")).thenReturn(member());
        var response = new MockHttpServletResponse();
        assertEquals(BigDecimal.ZERO, controller.summary("Bearer test-session", response).getData().pendingBonus());
        assertEquals("no-store", response.getHeader("Cache-Control")); verify(agents).selectByUserId(100L);
    }
}
