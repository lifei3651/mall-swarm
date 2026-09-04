package com.macro.mall.distribution.notification;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.WeChatSubscriptionService;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.mock.mini-program-enabled", havingValue = "false", matchIfMissing = true)
public class WeChatMiniProgramNotificationAdapter implements ExternalNotificationAdapter {
    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<Integer> RETRYABLE = Set.of(-1, 40001, 40014, 42001, 43108, 45009);
    private static final Set<Integer> INVALID_GRANT = Set.of(40003, 40037, 43101, 43107, 45168, 47003);

    private final WeChatMiniProgramProperties properties;
    private final WeChatSubscriptionService subscriptionService;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final WeChatMiniProgramGateway gateway;

    @Override public String channel() { return "MINI_PROGRAM"; }
    @Override public String providerCode() { return "WECHAT_SUBSCRIBE"; }

    @Override
    public GateDecision readiness(ExternalNotificationContext context) {
        if (!properties.subscribeMessageReady()) return GateDecision.deny("WECHAT_SUBSCRIBE_DISABLED");
        WeChatMiniProgramProperties.SubscriptionTemplate template = subscriptionService.template(context.getEventType());
        if (template == null) return GateDecision.deny("WECHAT_TEMPLATE_NOT_CONFIGURED");
        if (!validKey(template.getStatusKey()) || !validKey(template.getTimeKey())
                || !validKey(template.getRemarkKey()) || !validPage(template.getPage())) {
            return GateDecision.deny("WECHAT_TEMPLATE_MAPPING_INVALID");
        }
        return GateDecision.allow();
    }

    @Override public BigDecimal estimatedCost(ExternalNotificationContext context) { return BigDecimal.ZERO; }

    @Override
    public DeliveryResult send(ExternalNotificationContext context, String idempotencyKey) {
        WeChatMiniProgramProperties.SubscriptionTemplate template = subscriptionService.template(context.getEventType());
        if (template == null) return DeliveryResult.permanent("WECHAT_TEMPLATE_NOT_CONFIGURED");
        DmsWechatMiniProgramIdentity identity = identityDao.selectByMember(context.getTenantId(), appIdHash(), context.getMemberId());
        if (identity == null || identity.getOpenId() == null || identity.getOpenId().isBlank()
                || !context.getUserId().equals(identity.getUserId())) {
            return DeliveryResult.permanent("WECHAT_IDENTITY_MISSING");
        }
        if (subscriptionService.reserve(context.getTenantId(), context.getMemberId(),
                template.getTemplateId(), context.getTaskId()) == null) {
            return DeliveryResult.permanent("WECHAT_SUBSCRIPTION_NOT_AVAILABLE");
        }
        try {
            WeChatMiniProgramGateway.SubscribeMessageResult result = gateway.sendSubscribeMessage(
                    new WeChatMiniProgramGateway.SubscribeMessageCommand(identity.getOpenId(),
                            template.getTemplateId().trim(), safePage(template.getPage()),
                            properties.safeMiniProgramState(), templateData(template, context)));
            int code = result == null ? -1 : result.errorCode();
            if (code == 0) {
                subscriptionService.consume(context.getTenantId(), context.getTaskId());
                return DeliveryResult.delivered("wechat-task-" + context.getTaskId(), BigDecimal.ZERO);
            }
            if (INVALID_GRANT.contains(code)) {
                subscriptionService.invalidate(context.getTenantId(), context.getTaskId());
                return DeliveryResult.permanent("WECHAT_" + code);
            }
            if (RETRYABLE.contains(code)) return DeliveryResult.retryable("WECHAT_" + code);
            subscriptionService.invalidate(context.getTenantId(), context.getTaskId());
            return DeliveryResult.permanent("WECHAT_" + code);
        } catch (RuntimeException exception) {
            // 微信没有订阅消息查单接口；网络结果未知时按已消耗处理且不自动再次下发，避免重复提醒。
            subscriptionService.consume(context.getTenantId(), context.getTaskId());
            log.warn("微信订阅消息发送结果未知: taskId={}", context.getTaskId());
            return DeliveryResult.unknown(null, "WECHAT_RESULT_UNKNOWN");
        }
    }

    @Override
    public DeliveryResult query(ExternalNotificationContext context, DmsMessageDeliveryAttempt attempt) {
        return DeliveryResult.unknown(null, "WECHAT_QUERY_UNSUPPORTED");
    }

    private Map<String, String> templateData(WeChatMiniProgramProperties.SubscriptionTemplate template,
                                             ExternalNotificationContext context) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(template.getStatusKey().trim(), status(context.getEventType()));
        data.put(template.getTimeKey().trim(), LocalDateTime.now().format(MESSAGE_TIME));
        data.put(template.getRemarkKey().trim(), limitCodePoints(context.getSummary(), 20));
        return data;
    }

    private String status(String eventType) {
        return switch (eventType) {
            case "ORDER_SHIPPED" -> "已发货";
            case "AFTER_SALE_UPDATED" -> "已更新";
            case "REFUND_RESULT" -> "退款完成";
            case "WITHDRAW_PAID" -> "打款成功";
            default -> "已更新";
        };
    }

    private String appIdHash() { return SecureUtil.sha256(properties.getAppId().trim()); }
    private boolean validKey(String value) { return value != null && value.matches("^[a-z_]+[0-9]{1,3}$"); }
    private boolean validPage(String value) { return value == null || value.isBlank() || value.matches("^pages/[A-Za-z0-9_/?=&.-]{1,180}$"); }
    private String safePage(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String limitCodePoints(String value, int max) {
        String safe = value == null || value.isBlank() ? "请进入小程序查看详情" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        int count = safe.codePointCount(0, safe.length());
        return count <= max ? safe : safe.substring(0, safe.offsetByCodePoints(0, max));
    }
}
