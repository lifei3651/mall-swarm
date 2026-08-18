package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import java.util.List;

public interface MerchantService {
    List<DmsMerchant> listMerchants(String keyword, Integer status);
    DmsMerchant saveMerchant(DmsMerchant merchant);
    DmsMerchant updateMerchant(Long id, DmsMerchant merchant);
    boolean updateMerchantStatus(Long id, Integer status);
    List<DmsMerchantAccount> listAccounts(String keyword);
    List<DmsMerchantSettlement> listSettlements(Long merchantId, String status);
    List<DmsMerchantWithdrawal> listWithdrawals(Long merchantId, String status);
    List<DmsMerchantDepositFlow> listDepositFlows(Long merchantId);
    DmsMerchantDepositFlow freezeDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantDepositFlow receiveDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantDepositFlow releaseDeposit(MerchantDepositAdjustDTO dto);
    DmsMerchantWithdrawal applyWithdrawal(MerchantWithdrawalApplyDTO dto);
    DmsMerchantWithdrawal reviewWithdrawal(Long id, MerchantWithdrawalReviewDTO dto);
    DmsMerchantWithdrawal confirmPayment(Long id, MerchantWithdrawalPayDTO dto);
    DmsMerchantWithdrawal rejectWithdrawal(Long id, MerchantWithdrawalRejectDTO dto);
    void createOrderSettlements(Long orderId);
    void lockOrderSettlementEligibility(Long orderId);
    int releaseEligibleSettlements(int limit);
    void reverseAfterSaleItems(List<DmsShopAfterSaleItem> items);
}
