package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.ShopWalletServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentPasswordLockRaceTest {

    @Test
    void correctPasswordCannotProceedWhenConcurrentFailureLocksAccount() {
        DmsShopMemberDao memberDao = mock(DmsShopMemberDao.class);
        DmsAgentDao agentDao = mock(DmsAgentDao.class);
        MemberAssetService memberAssetService = mock(MemberAssetService.class);
        PaymentPasswordAttemptService attemptService = mock(PaymentPasswordAttemptService.class);
        DmsShopMember payer = member(1L, 1001L, "13900000001", "246810");
        DmsShopMember recipient = member(2L, 1002L, "13900000002", "135790");
        DmsShopMember locked = member(1L, 1001L, "13900000001", "246810");
        locked.setPayPasswordFailedCount(5);
        locked.setPayPasswordLockTime(LocalDateTime.now());

        when(memberDao.selectById(payer.getId())).thenReturn(payer, payer, payer, locked);
        when(memberDao.selectByPhone(recipient.getPhone())).thenReturn(recipient);
        DmsAgent recipientAgent = new DmsAgent();
        recipientAgent.setStatus(1);
        when(agentDao.selectByUserId(recipient.getUserId())).thenReturn(recipientAgent);
        when(attemptService.clearIfUnchanged(payer.getId(), 0)).thenReturn(false);

        ShopWalletServiceImpl service = new ShopWalletServiceImpl(
                memberDao, agentDao, mock(DmsMemberAssetAccountDao.class), mock(DmsShopOrderDao.class),
                memberAssetService, mock(ShopService.class), attemptService,
                mock(SmsVerificationService.class), mock(WithdrawService.class));
        BalanceTransferDTO dto = new BalanceTransferDTO();
        dto.setRecipientPhone(recipient.getPhone());
        dto.setAmount(BigDecimal.ONE);
        dto.setPaymentPassword("246810");

        ApiException error = assertThrows(ApiException.class, () -> service.transfer(payer, dto));
        assertEquals("支付密码连续错误5次，已锁定30分钟", error.getMessage());
        verify(memberAssetService, never()).transfer(org.mockito.ArgumentMatchers.any());
    }

    private DmsShopMember member(Long id, Long userId, String phone, String paymentPassword) {
        DmsShopMember member = new DmsShopMember();
        member.setId(id);
        member.setUserId(userId);
        member.setPhone(phone);
        member.setUsername("member" + id);
        member.setNickname("会员" + id);
        member.setStatus(1);
        member.setPayPasswordHash(BCrypt.hashpw(paymentPassword));
        member.setPayPasswordFailedCount(0);
        return member;
    }
}
