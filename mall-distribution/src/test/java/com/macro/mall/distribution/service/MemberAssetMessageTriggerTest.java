package com.macro.mall.distribution.service;

import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.MemberAssetServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberAssetMessageTriggerTest {

    @Test
    void persistedWalletFlowPublishesAStablePrivacySafeMessageFact() {
        DmsMemberAssetAccountDao accountDao = mock(DmsMemberAssetAccountDao.class);
        DmsMemberAssetFlowDao flowDao = mock(DmsMemberAssetFlowDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        DmsShopMemberDao memberDao = mock(DmsShopMemberDao.class);
        MemberMessageService messages = mock(MemberMessageService.class);
        DmsShopMember member = new DmsShopMember(); member.setId(7L); member.setUserId(70L);
        DmsMemberAssetAccount before = account(70L, "10.00");
        DmsMemberAssetAccount after = account(70L, "15.00");
        when(agentDao.selectByUserId(70L)).thenReturn(null);
        when(memberDao.selectByUserId(70L)).thenReturn(member);
        when(accountDao.selectByUserIdAndAssetCode(70L, BalanceAsset.CODE)).thenReturn(before, after);
        doAnswer(invocation -> { DmsMemberAssetFlow flow = invocation.getArgument(0); flow.setId(501L); return 1; })
                .when(flowDao).insert(any());
        MemberAssetServiceImpl service = new MemberAssetServiceImpl(accountDao, flowDao, agentDao, memberDao,
                mock(OperationLogService.class), messages);
        AssetChangeDTO change = new AssetChangeDTO(); change.setUserId(70L); change.setAmount(new BigDecimal("5.00"));
        change.setBizType("TEST"); change.setBizId("fact-1"); change.setRequestId("wallet-request-1");
        change.setRemark("测试钱包事实");

        service.issue(change);

        ArgumentCaptor<MemberMessageEvent> event = ArgumentCaptor.forClass(MemberMessageEvent.class);
        verify(messages).publish(event.capture());
        assertEquals("WALLET_FLOW:ADMWALLET-REQUEST-1", event.getValue().eventKey());
        assertEquals("WALLET_FLOW", event.getValue().eventType());
        assertEquals("WALLET_FUNDS", event.getValue().category());
        assertEquals("WALLET", event.getValue().targetType());
        assertEquals(70L, event.getValue().userId());
    }

    private DmsMemberAssetAccount account(Long userId, String balance) {
        DmsMemberAssetAccount account = new DmsMemberAssetAccount(); account.setId(1L); account.setUserId(userId);
        account.setAssetCode(BalanceAsset.CODE); account.setBalance(new BigDecimal(balance));
        return account;
    }
}
