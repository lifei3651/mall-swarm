package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.dto.PaymentPasswordDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.vo.BalanceRecipientVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopWalletSummaryVO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;

import java.util.List;

public interface ShopWalletService {

    ShopWalletSummaryVO getSummary(DmsShopMember member);

    BalanceRecipientVO findRecipient(DmsShopMember member, String phone);

    boolean setPaymentPassword(DmsShopMember member, PaymentPasswordDTO dto);

    boolean transfer(DmsShopMember member, BalanceTransferDTO dto);

    ShopOrderVO payOrder(DmsShopMember member, Long orderId, BalancePayDTO dto);

    WithdrawRecordVO applyWithdrawal(DmsShopMember member, ShopWithdrawalApplyDTO dto);

    List<WithdrawRecordVO> listWithdrawals(DmsShopMember member);

    List<DmsMemberAssetFlow> listBalanceFlows(DmsShopMember member);
}
