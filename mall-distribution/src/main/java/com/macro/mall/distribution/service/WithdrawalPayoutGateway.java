package com.macro.mall.distribution.service;

import java.math.BigDecimal;

/** 官方渠道转账适配器。SUCCESS 必须代表适配器已经完成订单、金额和收款人核对。 */
public interface WithdrawalPayoutGateway {
    boolean supports(Integer withdrawType);
    boolean configured();
    PayoutResult initiate(PayoutCommand command);
    PayoutResult query(PayoutCommand command, String providerOrderNo);

    record PayoutCommand(Long withdrawId, String requestNo, Long memberId, Long userId,
                         BigDecimal amount, String recipientAccount, String recipientName) {
    }

    record PayoutResult(State state, String requestNo, String providerOrderNo, String providerStatus,
                        BigDecimal amount, String recipientHash, String responseDigest,
                        String failureCode, String confirmationPackage) {
    }

    enum State {
        PROCESSING, WAIT_USER_CONFIRM, SUCCESS, FAILED, UNKNOWN
    }
}
