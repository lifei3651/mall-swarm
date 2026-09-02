package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.vo.MerchantBalanceReconciliationVO;
import com.macro.mall.distribution.vo.MerchantExitReadinessVO;
import java.util.List;

public interface MerchantService {
    List<DmsMerchant> listMerchants(String keyword, Integer status);
    DmsMerchant saveMerchant(DmsMerchant merchant);
    DmsMerchant onboardMerchant(MerchantOnboardingDTO dto);
    DmsMerchant updateMerchant(Long id, DmsMerchant merchant);
    DmsMerchant currentMerchantProfile();
    DmsMerchant submitCurrentMerchantProfile(MerchantProfileSubmitDTO dto);
    boolean updateMerchantStatus(Long id, Integer status);
    DmsMerchant updateMerchantControls(Long id, MerchantControlDTO dto);
    MerchantExitReadinessVO getExitReadiness(Long id);
    void assertOrderCanBePaid(Long orderId);
    List<DmsMerchantAccount> listAccounts(String keyword);
    List<DmsMerchantSettlement> listSettlements(Long merchantId, String status);
    List<DmsMerchantWithdrawal> listWithdrawals(Long merchantId, String status);
    List<DmsMerchantDepositFlow> listDepositFlows(Long merchantId);
    List<DmsMerchantLedger> listLedgers(Long merchantId, String bizType);
    List<MerchantBalanceReconciliationVO> reconcileBalances(Long merchantId);
    List<DmsMerchantWithdrawalEvent> listWithdrawalEvents(Long withdrawalId);
    DmsMerchantDepositFlow freezeDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantDepositFlow receiveDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantDepositFlow releaseDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantWithdrawal applyWithdrawal(MerchantWithdrawalApplyDTO dto);
    DmsMerchantWithdrawal reviewWithdrawal(Long id, MerchantWithdrawalReviewDTO dto);
    DmsMerchantWithdrawal confirmPayment(Long id, MerchantWithdrawalPayDTO dto);
    DmsMerchantWithdrawal rejectWithdrawal(Long id, MerchantWithdrawalRejectDTO dto);
    DmsMerchantWithdrawal startWithdrawalPayment(Long id);
    DmsMerchantWithdrawal markWithdrawalPaymentFailed(Long id, MerchantWithdrawalActionDTO dto);
    DmsMerchantWithdrawal cancelWithdrawal(Long id, MerchantWithdrawalActionDTO dto);
    DmsMerchantWithdrawal riskFreezeWithdrawal(Long id, MerchantWithdrawalActionDTO dto);
    DmsMerchantWithdrawal resumeWithdrawal(Long id, MerchantWithdrawalActionDTO dto);
    DmsMerchantWithdrawal completeWithdrawal(Long id);
    void createOrderSettlements(Long orderId);
    void lockOrderSettlementEligibility(Long orderId);
    int releaseEligibleSettlements(int limit);
    void reverseAfterSaleItems(List<DmsShopAfterSaleItem> items);
}
