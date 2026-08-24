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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
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
    private final MemberMessageService memberMessageService;
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

    public OrderRealtimeService(ObjectMapper objectMapper, MemberMessageService memberMessageService) {
        this.objectMapper = objectMapper;
        this.memberMessageService = memberMessageService;
    }

    public SseEmitter subscribeMember(Long tenantId, Long userId) {
        return subscribe("member", tenantId == null ? 1L : tenantId, userId == null ? 0L : userId);
    }

    public SseEmitter subscribeAdmin(Long tenantId) {
        Long safeTenantId = tenantId == null ? 1L : tenantId;
        return subscribe("admin", safeTenantId, safeTenantId);
    }

    public void orderChanged(DmsShopOrder order, String changeType) {
        if (order == null) return;
        orderChanged(order, changeType, order.getId());
    }

    public void orderChanged(DmsShopOrder order, String changeType, Long eventObjectId) {
        if (order == null) return;
        orderChanged(order.getTenantId(), order.getUserId(), order.getId(), changeType, eventObjectId);
    }

    public void orderChanged(Long tenantId, Long userId, Long orderId, String changeType) {
        orderChanged(tenantId, userId, orderId, changeType, orderId);
    }

    public void orderChanged(Long tenantId, Long userId, Long orderId, String changeType, Long eventObjectId) {
        RealtimeEvent event = new RealtimeEvent(
                UUID.randomUUID().toString(),
                tenantId == null ? 1L : tenantId,
                userId,
                orderId,
                changeType == null || changeType.isBlank() ? "ORDER_CHANGED" : changeType,
                LocalDateTime.now().toString()
        );
        Runnable publish = () -> {
            publishBusinessMessage(event, eventObjectId);
            dispatch(() -> publishNow(event));
        };
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

    private void publishBusinessMessage(RealtimeEvent event, Long eventObjectId) {
        if (memberMessageService == null || event.userId() == null) return;
        String changeType = event.changeType();
        String eventType;
        String category;
        String targetType;
        Long targetId;
        Long targetParentId;
        if ("ORDER_PAID".equals(changeType)) eventType = "ORDER_PAID";
        else if (Set.of("ORDER_CANCELLED", "ORDER_TIMEOUT_CLOSED").contains(changeType)) eventType = "ORDER_CLOSED";
        else if ("ORDER_SHIPPED".equals(changeType)) eventType = "ORDER_SHIPPED";
        else if ("ORDER_RECEIVED".equals(changeType)) eventType = "ORDER_RECEIVED";
        else if ("AFTER_SALE_APPLIED".equals(changeType)) eventType = "AFTER_SALE_APPLIED";
        else if ("AFTER_SALE_COMPLETED".equals(changeType)) eventType = "REFUND_RESULT";
        else if (changeType != null && changeType.startsWith("AFTER_SALE_")) eventType = "AFTER_SALE_UPDATED";
        else return;
        boolean afterSale = changeType.startsWith("AFTER_SALE_");
        category = afterSale ? "AFTER_SALE_REFUND" : "ORDER_LOGISTICS";
        targetType = afterSale ? "AFTER_SALE" : "ORDER";
        targetId = afterSale ? eventObjectId : event.orderId();
        targetParentId = afterSale ? event.orderId() : null;
        Long stableObjectId = eventObjectId == null ? event.orderId() : eventObjectId;
        memberMessageService.publish(new MemberMessageEvent(event.tenantId(), event.userId(),
                changeType + ":" + stableObjectId, eventType, category, targetType,
                targetId, targetParentId, LocalDateTime.now()));
    }

    /** 消息已在独立事务中成功落库后，复用既有连接通知在线页面刷新未读数。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void messageCreated(MemberMessageCreatedEvent created) {
        if (created == null) return;
        RealtimeEvent event = new RealtimeEvent(UUID.randomUUID().toString(), created.tenantId(),
                created.userId(), created.messageId(), "MESSAGE_CREATED", LocalDateTime.now().toString());
        dispatch(() -> publishNow(event));
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

    private synchronized SseEmitter subscribe(String audience, Long tenantId, Long audienceId) {
        int totalLimit = Math.max(1, maxConnections);
        int principalLimit = Math.max(1, maxConnectionsPerPrincipal);
        if (subscriptions.size() >= totalLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "实时连接已满，请稍后重试");
        }
        long principalConnections = subscriptions.values().stream()
                .filter(item -> audience.equals(item.audience()) && tenantId.equals(item.tenantId())
                        && audienceId.equals(item.audienceId()))
                .count();
        if (principalConnections >= principalLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前账号实时连接过多，请关闭重复页面后重试");
        }
        // 连接到期后必须重新经过身份认证，缩短账号停用或会话撤销后的通知暴露窗口。
        SseEmitter emitter = new SseEmitter(Math.max(60_000L, connectionTimeoutMs));
        String id = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(audience, tenantId, audienceId, emitter);
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
                    && event.tenantId().equals(subscription.tenantId())
                    && event.userId() != null && event.userId().equals(subscription.audienceId());
            boolean adminMatch = "admin".equals(subscription.audience())
                    && event.tenantId().equals(subscription.tenantId());
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

    private record Subscription(String audience, Long tenantId, Long audienceId, SseEmitter emitter) {
    }

    public record RealtimeEvent(String eventId, Long tenantId, Long userId, Long orderId,
                                String changeType, String occurredAt) {
    }
}
