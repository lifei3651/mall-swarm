package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMemberAssetAccountDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.AssetTransferDTO;
import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.service.impl.MemberAssetServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAssetClosedModeTest {

    @Mock private DmsMemberAssetAccountDao accounts;
    @Mock private DmsMemberAssetFlowDao flows;
    @Mock private DmsAgentDao agents;
    @Mock private DmsShopMemberDao members;
    @Mock private OperationLogService operationLog;
    @Mock private MemberMessageService messages;
    @Mock private BalanceTransactionModeService mode;
    @InjectMocks private MemberAssetServiceImpl assets;

    @BeforeEach void useTestTenant() { TenantContext.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void manualCreditAndDebitAreBlockedBeforeAssetMutation() {
        doThrow(new ApiException("本商城已关闭新余额交易"))
                .when(mode).requireEnabledForNewTransaction(1L);
        AssetChangeDTO change = manual();

        assertThrows(ApiException.class, () -> assets.issue(change));
        assertThrows(ApiException.class, () -> assets.deduct(change));

        verify(accounts, never()).addBalance(any(), any(), any());
        verify(flows, never()).insert(any());
    }

    @Test
    void balancePaymentAndTransferCannotBypassWalletEndpoint() {
        doThrow(new ApiException("本商城已关闭新余额交易"))
                .when(mode).requireEnabledForNewTransaction(1L);
        AssetChangeDTO payment = manual(); payment.setBizType("ORDER_BALANCE_PAYMENT");
        AssetTransferDTO transfer = new AssetTransferDTO(); transfer.setAmount(BigDecimal.ONE);

        assertThrows(ApiException.class, () -> assets.consume(payment));
        assertThrows(ApiException.class, () -> assets.transfer(transfer));

        verify(accounts, never()).subtractBalance(any(), any(), any(), any());
    }

    @Test
    void historicalBalanceRefundCreditRemainsAvailable() {
        DmsShopMember member = new DmsShopMember(); member.setId(1L); member.setUserId(101L);
        DmsMemberAssetAccount before = account("10.00");
        DmsMemberAssetAccount after = account("11.00");
        when(members.selectByUserId(101L)).thenReturn(member);
        when(accounts.selectByUserIdAndAssetCode(101L, BalanceAsset.CODE)).thenReturn(before, after);
        AssetChangeDTO refund = manual(); refund.setBizType("ORDER_BALANCE_REFUND");

        assertNotNull(assets.issue(refund));

        verify(accounts).addBalanceByUserId(101L, BalanceAsset.CODE, BigDecimal.ONE);
        verify(mode, never()).requireEnabledForNewTransaction(any());
    }

    private DmsMemberAssetAccount account(String amount) {
        DmsMemberAssetAccount account = new DmsMemberAssetAccount();
        account.setUserId(101L); account.setAssetCode(BalanceAsset.CODE);
        account.setBalance(new BigDecimal(amount));
        return account;
    }

    private AssetChangeDTO manual() {
        AssetChangeDTO dto = new AssetChangeDTO();
        dto.setUserId(101L); dto.setAmount(BigDecimal.ONE);
        dto.setBizType("MANUAL_MEMBER_ADJUST"); dto.setBizId("test-1");
        dto.setRequestId("manual-test-1"); dto.setRemark("测试关闭门禁");
        return dto;
    }
}
