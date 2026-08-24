package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class MockMiniProgramAdapter implements ExternalNotificationAdapter {
    private final ExternalNotificationProperties external;
    private final MockNotificationProperties mock;
    @Override public String channel() { return "MINI_PROGRAM"; }
    @Override public String providerCode() { return "SIMULATOR_ONLY"; }
    @Override public GateDecision readiness(ExternalNotificationContext context) {
        return external.isEnabled() && external.isWorkerEnabled() && mock.isEnabled() && mock.isMiniProgramEnabled()
                ? GateDecision.allow() : GateDecision.deny("REAL_PROVIDER_NOT_CONNECTED");
    }
    @Override public BigDecimal estimatedCost(ExternalNotificationContext context) { return new BigDecimal("0.0100"); }
    @Override public DeliveryResult send(ExternalNotificationContext context, String idempotencyKey) { return DeliveryResult.accepted("mock-mini-" + context.getTaskId(), BigDecimal.ZERO); }
    @Override public DeliveryResult query(ExternalNotificationContext context, DmsMessageDeliveryAttempt attempt) { return DeliveryResult.delivered(attempt.getProviderMessageId(), BigDecimal.ZERO); }
}
