package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 订单与售后状态的单向实时通知。只发送“数据已变化”信号，页面仍通过原查询接口读取权威数据。
 */
@Slf4j
@Service
public class OrderRealtimeService {

    private final ObjectMapper objectMapper;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor senderExecutor = new ThreadPoolExecutor(
            2, 8, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), runnable -> {
        Thread thread = new Thread(runnable, "order-realtime-sender");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    @Value("${shop.realtime.max-connections:500}")
    private int maxConnections = 500;

    @Value("${shop.realtime.max-connections-per-principal:5}")
    private int maxConnectionsPerPrincipal = 5;

    @Value("${shop.realtime.connection-timeout-ms:600000}")
    private long connectionTimeoutMs = 10L * 60L * 1000L;

    public OrderRealtimeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
        Runnable publish = () -> dispatch(() -> publishNow(event));
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
            dispatch(() -> {
                try {
                    subscription.emitter().send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception ex) {
                    remove(id, subscription.emitter());
                }
            });
        });
    }

    private synchronized SseEmitter subscribe(String audience, Long audienceId) {
        int totalLimit = Math.max(1, maxConnections);
        int principalLimit = Math.max(1, maxConnectionsPerPrincipal);
        if (subscriptions.size() >= totalLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "实时连接已满，请稍后重试");
        }
        long principalConnections = subscriptions.values().stream()
                .filter(item -> audience.equals(item.audience()) && audienceId.equals(item.audienceId()))
                .count();
        if (principalConnections >= principalLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前账号实时连接过多，请关闭重复页面后重试");
        }
        // 连接到期后必须重新经过身份认证，缩短账号停用或会话撤销后的通知暴露窗口。
        SseEmitter emitter = new SseEmitter(Math.max(60_000L, connectionTimeoutMs));
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

    private void dispatch(Runnable task) {
        try {
            senderExecutor.execute(task);
        } catch (RuntimeException ex) {
            // 实时通知只是刷新信号，队列满时允许丢弃，权威状态仍由查询接口返回。
            log.warn("ORDER_REALTIME_QUEUE_FULL active={} queued={}",
                    senderExecutor.getActiveCount(), senderExecutor.getQueue().size());
        }
    }

    @PreDestroy
    public void shutdown() {
        senderExecutor.shutdownNow();
        subscriptions.forEach((id, subscription) -> remove(id, subscription.emitter()));
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
