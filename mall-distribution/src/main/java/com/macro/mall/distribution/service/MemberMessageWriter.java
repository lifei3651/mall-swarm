package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsMemberMessageDao;
import com.macro.mall.distribution.dao.DmsMessageChannelConfigDao;
import com.macro.mall.distribution.dao.DmsMessageDeliveryTaskDao;
import com.macro.mall.distribution.dao.DmsMessageTemplateDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsMemberMessage;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.entity.DmsMessageTemplate;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.notification.ExternalNotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberMessageWriter {
    private static final Set<String> CATEGORIES = Set.of("ORDER_LOGISTICS", "AFTER_SALE_REFUND",
            "WALLET_FUNDS", "ACCOUNT_SECURITY", "SERVICE");
    private static final Set<String> TARGETS = Set.of("NONE", "ORDER", "AFTER_SALE", "WALLET",
            "WITHDRAWAL", "ACCOUNT_SECURITY");
    private final DmsShopMemberDao memberDao;
    private final DmsMemberMessageDao messageDao;
    private final DmsMessageTemplateDao templateDao;
    private final DmsMessageChannelConfigDao channelDao;
    private final DmsMessageDeliveryTaskDao deliveryDao;
    private final ApplicationEventPublisher eventPublisher;
    private final ExternalNotificationProperties externalProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(MemberMessageEvent event) {
        if (event == null || event.tenantId() == null || event.userId() == null
                || !hasText(event.eventKey()) || !hasText(event.eventType())
                || !CATEGORIES.contains(event.category()) || !TARGETS.contains(event.targetType())
                || !safeTarget(event)) {
            log.warn("MEMBER_MESSAGE_REJECTED invalid safe event");
            return;
        }
        DmsShopMember member = memberDao.selectByUserId(event.userId());
        if (member == null || member.getId() == null || !Integer.valueOf(1).equals(member.getStatus())
                || Integer.valueOf(1).equals(member.getSystemAccount())) return;
        DmsMessageTemplate template = templateDao.selectByEventType(event.tenantId(), event.eventType());
        DmsMessageChannelConfig channels = channelDao.selectByEventType(event.tenantId(), event.eventType());
        if (template == null || !Integer.valueOf(1).equals(template.getEnabled())
                || channels == null || !Integer.valueOf(1).equals(channels.getInAppEnabled())) return;

        DmsMemberMessage message = new DmsMemberMessage();
        message.setTenantId(event.tenantId());
        message.setMemberId(member.getId());
        message.setUserId(event.userId());
        message.setEventKey(limit(event.eventKey(), 160));
        message.setEventType(limit(event.eventType(), 64));
        message.setCategory(event.category());
        // 模板在此刻渲染成不可变快照；查询消息时不再关联模板。
        message.setTitle(limit(template.getTitleTemplate(), 128));
        message.setSummary(limit(template.getSummaryTemplate(), 300));
        message.setContent(limit(template.getContentTemplate(), 1000));
        message.setTargetType(event.targetType());
        message.setTargetId(event.targetId());
        message.setTargetParentId(event.targetParentId());
        message.setOccurredTime(event.occurredTime() == null ? LocalDateTime.now() : event.occurredTime());
        try {
            if (messageDao.insertIgnore(message) == 0 || message.getId() == null) return;
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        // 发送记录和外部适配器只是旁路；即使记录表或未来外部提供商失败，已落库的站内消息也必须保留。
        recordDeliverySafely(message, event.eventType(), "IN_APP", "SUCCESS", null, LocalDateTime.now(), true, BigDecimal.ZERO);
        recordDeliverySafely(message, event.eventType(), "SMS", externalStatus(channels.getSmsEnabled()), externalCode(channels.getSmsEnabled()), null,
                Integer.valueOf(1).equals(channels.getSmsEnabled()), channels.getEstimatedSmsCost());
        recordDeliverySafely(message, event.eventType(), "APP_PUSH", externalStatus(channels.getAppPushEnabled()), externalCode(channels.getAppPushEnabled()), null,
                Integer.valueOf(1).equals(channels.getAppPushEnabled()), BigDecimal.ZERO);
        recordDeliverySafely(message, event.eventType(), "MINI_PROGRAM", externalStatus(channels.getMiniProgramEnabled()), externalCode(channels.getMiniProgramEnabled()), null,
                Integer.valueOf(1).equals(channels.getMiniProgramEnabled()), BigDecimal.ZERO);
        eventPublisher.publishEvent(new MemberMessageCreatedEvent(event.tenantId(), event.userId(), message.getId()));
    }

    private void recordDeliverySafely(DmsMemberMessage message, String eventType, String channel, String status, String errorCode,
                                      LocalDateTime sentTime, boolean channelEnabled, BigDecimal estimatedCost) {
        try {
            recordDelivery(message, eventType, channel, status, errorCode, sentTime, channelEnabled, estimatedCost);
        } catch (RuntimeException ex) {
            log.error("MEMBER_MESSAGE_DELIVERY_RECORD_FAILED messageId={} channel={} status={}",
                    message.getId(), channel, status);
        }
    }

    private void recordDelivery(DmsMemberMessage message, String eventType, String channel, String status, String errorCode,
                                LocalDateTime sentTime, boolean channelEnabled, BigDecimal estimatedCost) {
        DmsMessageDeliveryTask task = new DmsMessageDeliveryTask();
        task.setTenantId(message.getTenantId());
        task.setMessageId(message.getId());
        task.setEventType(eventType);
        task.setChannel(channel);
        task.setIdempotencyKey(message.getTenantId() + ":" + message.getId() + ":" + channel);
        task.setStatus(status);
        task.setRetryCount(0);
        task.setAttemptCount(0);
        task.setMaxAttempts(Math.max(1, externalProperties.getMaxAttempts()));
        task.setEstimatedCost(estimatedCost == null || estimatedCost.signum() < 0 ? BigDecimal.ZERO : estimatedCost);
        task.setActualCost(BigDecimal.ZERO);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorCode == null ? null : (channelEnabled ? "应用启动门禁未开启" : "事件外部渠道未开启"));
        task.setExpiresAt("IN_APP".equals(channel) ? null : LocalDateTime.now().plusHours(Math.max(1, externalProperties.getTaskTtlHours())));
        task.setSentTime(sentTime);
        deliveryDao.insertIgnore(task);
    }

    private String externalStatus(Integer channelEnabled) {
        return Integer.valueOf(1).equals(channelEnabled) && externalProperties.isEnabled() && externalProperties.isWorkerEnabled()
                ? "PENDING" : "SUPPRESSED";
    }
    private String externalCode(Integer channelEnabled) {
        if (!Integer.valueOf(1).equals(channelEnabled)) return "CHANNEL_DISABLED";
        return externalProperties.isEnabled() && externalProperties.isWorkerEnabled() ? null : "STARTUP_GATE_DISABLED";
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private boolean safeTarget(MemberMessageEvent event) {
        if ("NONE".equals(event.targetType())) return event.targetId() == null && event.targetParentId() == null;
        if ("ORDER".equals(event.targetType()) || "WITHDRAWAL".equals(event.targetType())) {
            return positive(event.targetId()) && event.targetParentId() == null;
        }
        if ("AFTER_SALE".equals(event.targetType())) {
            return positive(event.targetId()) && positive(event.targetParentId());
        }
        return event.targetParentId() == null;
    }
    private boolean positive(Long value) { return value != null && value > 0L; }
    private String limit(String value, int max) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
