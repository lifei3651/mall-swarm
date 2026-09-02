package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import com.macro.mall.distribution.vo.WechatTransferConfirmationVO;

public interface WithdrawalPayoutService {
    /** 当前渠道是否已完成客户签约与安全配置，可用于自动打款。 */
    boolean isReady(Integer withdrawType);
    /** 审核通过前确认对应提现单可以立即进入官方渠道打款。 */
    void requireReady(Long withdrawId);
    WithdrawalPayoutVO start(Long withdrawId);
    WithdrawalPayoutVO reconcile(Long withdrawId);
    WithdrawalPayoutVO get(Long withdrawId);
    WechatTransferConfirmationVO prepareWechatConfirmation(DmsShopMember member, Long withdrawId);
}
