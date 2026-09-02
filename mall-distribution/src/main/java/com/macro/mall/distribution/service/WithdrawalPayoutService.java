package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import com.macro.mall.distribution.vo.WechatTransferConfirmationVO;

public interface WithdrawalPayoutService {
    WithdrawalPayoutVO start(Long withdrawId);
    WithdrawalPayoutVO reconcile(Long withdrawId);
    WithdrawalPayoutVO get(Long withdrawId);
    WechatTransferConfirmationVO prepareWechatConfirmation(DmsShopMember member, Long withdrawId);
}
