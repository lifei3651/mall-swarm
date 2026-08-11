package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单与售后状态的单向实时通知。只发送“数据已变化”信号，页面仍通过原查询接口读取权威数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRealtimeService {

    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;
    private final ObjectMapper objectMapper;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter subscribeMember(Long userId) {
        return subscribe("member", userId == null ? 0L : userId);
    }

    public SseEmitter subscribeAdmin(Long tenantId) {
        return subscribe("admin", tenantId == null ? 1L : tenantId);
    }

    public void orderChanged(DmsShopOrder order, String changeType) {
        if (order == null) return;
        orderChanged(order.getTenantId(), order.getUserId(), order.getId(), changeType);
    }

    public void orderChanged(Long tenantId, Long userId, Long orderId, String changeType) {
        RealtimeEvent event = new RealtimeEvent(
                UUID.randomUUID().toString(),
                tenantId == null ? 1L : tenantId,
                userId,
                orderId,
                changeType == null || changeType.isBlank() ? "ORDER_CHANGED" : changeType,
                LocalDateTime.now().toString()
        );
        Runnable publish = () -> publishNow(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    @Scheduled(fixedDelayString = "${shop.realtime.heartbeat-ms:25000}")
    public void heartbeat() {
        subscriptions.forEach((id, subscription) -> {
            try {
                subscription.emitter().send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception ex) {
                remove(id, subscription.emitter());
            }
        });
    }

    private SseEmitter subscribe(String audience, Long audienceId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        String id = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(audience, audienceId, emitter);
        subscriptions.put(id, subscription);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> remove(id, emitter));
        emitter.onError(error -> remove(id, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"connected\":true}"));
        } catch (IOException ex) {
            remove(id, emitter);
        }
        return emitter;
    }

    private void publishNow(RealtimeEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            log.warn("ORDER_REALTIME_SERIALIZE_FAILED type={} orderId={}", event.changeType(), event.orderId());
            return;
        }
        subscriptions.forEach((id, subscription) -> {
            boolean memberMatch = "member".equals(subscription.audience())
                    && event.userId() != null && event.userId().equals(subscription.audienceId());
            boolean adminMatch = "admin".equals(subscription.audience())
                    && event.tenantId().equals(subscription.audienceId());
            if (!memberMatch && !adminMatch) return;
            try {
                subscription.emitter().send(SseEmitter.event()
                        .id(event.eventId())
                        .name("order.changed")
                        .data(payload));
            } catch (Exception ex) {
                remove(id, subscription.emitter());
            }
        });
    }

    private void remove(String id, SseEmitter emitter) {
        subscriptions.remove(id);
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 连接已由浏览器或容器关闭，无需重复处理。
        }
    }

    private record Subscription(String audience, Long audienceId, SseEmitter emitter) {
    }

    public record RealtimeEvent(String eventId, Long tenantId, Long userId, Long orderId,
                                String changeType, String occurredAt) {
    }
}
