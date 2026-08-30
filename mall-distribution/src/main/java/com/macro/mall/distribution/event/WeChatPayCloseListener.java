package com.macro.mall.distribution.event;

import com.macro.mall.distribution.service.WeChatPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WeChatPayCloseListener {

    private final WeChatPayService weChatPayService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void closeAfterLocalCommit(WeChatPayCloseEvent event) {
        if (event != null) weChatPayService.closeOrder(event.paymentNo());
    }
}
