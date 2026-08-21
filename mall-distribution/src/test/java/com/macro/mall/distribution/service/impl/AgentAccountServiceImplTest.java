package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsAgentAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsAgentAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AgentAccountServiceImplTest {

    @Test
    void subtractUnsettledCommissionLocksAccountBeforeCheckingBalance() {
        DmsAgentAccountDao accountDao = mock(DmsAgentAccountDao.class);
        DmsShopMemberDao memberDao = mock(DmsShopMemberDao.class);
        DmsAgentAccount account = new DmsAgentAccount();
        account.setUnsettledCommission(new BigDecimal("100.00"));
        when(accountDao.selectByAgentIdForUpdate(10L)).thenReturn(account);
        when(accountDao.subtractUnsettledCommission(10L, new BigDecimal("30.00"))).thenReturn(1);
        AgentAccountServiceImpl service = new AgentAccountServiceImpl(accountDao, memberDao);

        assertTrue(service.subtractUnsettledCommission(10L, new BigDecimal("30.00")));

        verify(accountDao).selectByAgentIdForUpdate(10L);
        verify(accountDao, never()).selectByAgentId(10L);
        verify(accountDao).subtractUnsettledCommission(10L, new BigDecimal("30.00"));
    }
}
