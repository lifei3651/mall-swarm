package com.macro.mall.distribution.notification;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.QuerySendDetailsRequest;
import com.aliyun.dysmsapi20170525.models.QuerySendDetailsResponse;
import com.aliyun.dysmsapi20170525.models.QuerySendDetailsResponseBody.QuerySendDetailsResponseBodySmsSendDetailDTOsSmsSendDetailDTO;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class AliyunNotificationSmsAdapter implements ExternalNotificationAdapter {
    private static final Set<String> ALLOWED_SERVICE_EVENTS = Set.of(
            "LOGIN_PASSWORD_CHANGED", "PAY_PASSWORD_CHANGED", "PHONE_CHANGED",
            "ORDER_SHIPPED", "AFTER_SALE_UPDATED", "REFUND_RESULT");
    private static final Set<String> RETRYABLE_CODES = Set.of(
            "isp.SYSTEM_ERROR", "isv.BUSINESS_LIMIT_CONTROL", "isv.AMOUNT_NOT_ENOUGH", "HTTP_TIMEOUT");
    private static final DateTimeFormatter SEND_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AliyunNotificationSmsProperties properties;
    private final ExternalNotificationProperties external;
    private final ObjectMapper objectMapper;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    @Override public String channel() { return "SMS"; }
    @Override public String providerCode() { return "ALIYUN_NOTIFICATION_SMS"; }

    @Override
    public GateDecision readiness(ExternalNotificationContext context) {
        if (!external.isEnabled() || !external.isWorkerEnabled() || !properties.isEnabled()) return GateDecision.deny("STARTUP_GATE_DISABLED");
        if (context == null || !ALLOWED_SERVICE_EVENTS.contains(context.getEventType())) return GateDecision.deny("EVENT_NOT_ALLOWED_FOR_SMS");
        if (blank(context.getPhone()) || !context.getPhone().matches("^1[3-9]\\d{9}$")) return GateDecision.deny("NO_QUALIFIED_PHONE");
        if (blank(properties.getAccessKeyId()) || blank(properties.getAccessKeySecret()) || blank(properties.getSignName())) return GateDecision.deny("SMS_CREDENTIALS_MISSING");
        if (blank(properties.getTemplates().get(context.getEventType()))) return GateDecision.deny("APPROVED_TEMPLATE_MISSING");
        return GateDecision.allow();
    }

    @Override public BigDecimal estimatedCost(ExternalNotificationContext context) {
        return properties.getUnitCost() == null || properties.getUnitCost().signum() < 0 ? BigDecimal.ZERO : properties.getUnitCost();
    }

    @Override
    public DeliveryResult send(ExternalNotificationContext context, String idempotencyKey) {
        GateDecision gate = readiness(context);
        if (!gate.allowed()) return new DeliveryResult(DeliveryState.SUPPRESSED, null, BigDecimal.ZERO, gate.code(), "通知短信配置门禁未通过");
        try {
            SendSmsRequest request = new SendSmsRequest().setPhoneNumbers(context.getPhone())
                    .setSignName(properties.getSignName()).setTemplateCode(properties.getTemplates().get(context.getEventType()))
                    .setTemplateParam("{}").setOutId(shortDigest(idempotencyKey));
            SendSmsResponse response = client().sendSms(request);
            if (response == null || response.getBody() == null) return DeliveryResult.unknown(null, "EMPTY_PROVIDER_RESPONSE");
            String code = safeCode(response.getBody().getCode());
            if ("OK".equals(code)) return DeliveryResult.accepted(response.getBody().getBizId(), estimatedCost(context));
            return RETRYABLE_CODES.contains(code) ? DeliveryResult.retryable(code) : DeliveryResult.permanent(code);
        } catch (Exception ignored) {
            // 网络异常可能发生在供应商已受理之后，必须进入查询恢复，不能盲目重发。
            return DeliveryResult.unknown(null, "PROVIDER_RESULT_UNKNOWN");
        }
    }

    @Override
    public DeliveryResult query(ExternalNotificationContext context, DmsMessageDeliveryAttempt attempt) {
        if (attempt == null || blank(attempt.getProviderMessageId())) return DeliveryResult.unknown(null, "PROVIDER_ID_UNAVAILABLE");
        try {
            String date = (attempt.getSubmittedTime() == null ? Instant.now() : attempt.getSubmittedTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant())
                    .atZone(ZoneId.of("Asia/Shanghai")).format(SEND_DATE);
            QuerySendDetailsRequest request = new QuerySendDetailsRequest().setBizId(attempt.getProviderMessageId())
                    .setPhoneNumber(context.getPhone()).setSendDate(date).setCurrentPage(1L).setPageSize(10L);
            QuerySendDetailsResponse response = client().querySendDetails(request);
            if (response == null || response.getBody() == null || !"OK".equals(response.getBody().getCode())
                    || response.getBody().getSmsSendDetailDTOs() == null) return DeliveryResult.unknown(attempt.getProviderMessageId(), "QUERY_RESULT_UNKNOWN");
            List<QuerySendDetailsResponseBodySmsSendDetailDTOsSmsSendDetailDTO> rows = response.getBody().getSmsSendDetailDTOs().getSmsSendDetailDTO();
            if (rows == null || rows.isEmpty() || rows.get(0).getSendStatus() == null) return DeliveryResult.unknown(attempt.getProviderMessageId(), "QUERY_RESULT_UNKNOWN");
            long status = rows.get(0).getSendStatus();
            if (status == 2L) return DeliveryResult.delivered(attempt.getProviderMessageId(), estimatedCost(context));
            if (status == 3L) return DeliveryResult.permanent(safeCode(rows.get(0).getErrCode()));
            return DeliveryResult.unknown(attempt.getProviderMessageId(), "PROVIDER_PROCESSING");
        } catch (Exception ignored) {
            return DeliveryResult.unknown(attempt.getProviderMessageId(), "QUERY_TEMPORARILY_UNAVAILABLE");
        }
    }

    @Override
    public boolean verifyReceipt(Map<String, String> headers, byte[] body) {
        try {
            String timestamp = headers.get("x-notification-timestamp");
            String signature = headers.get("x-notification-signature");
            if (blank(timestamp) || blank(signature) || blank(properties.getReceiptSecret())) return false;
            long epoch = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - epoch) > 300) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getReceiptSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body == null ? new byte[0] : body);
            byte[] supplied = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception ignored) { return false; }
    }

    @Override
    public NotificationReceipt parseReceipt(byte[] body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return new NotificationReceipt(text(json, "receiptId"), longValue(json, "taskId"),
                    text(json, "status"), text(json, "providerMessageId"), text(json, "errorCode"));
        } catch (Exception ignored) { throw new IllegalArgumentException("回执格式不正确"); }
    }

    Client client() throws Exception {
        String key = properties.getAccessKeyId() + "@" + properties.getEndpoint();
        Client cached = clients.get(key); if (cached != null) return cached;
        Config config = new Config().setAccessKeyId(properties.getAccessKeyId()).setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint(properties.getEndpoint()).setConnectTimeout(timeout(properties.getConnectTimeoutMs(), 5000))
                .setReadTimeout(timeout(properties.getReadTimeoutMs(), 10000));
        Client created = new Client(config); clients.put(key, created); return created;
    }
    private int timeout(int value, int fallback) { return Math.max(1000, Math.min(value <= 0 ? fallback : value, 60000)); }
    private String shortDigest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (Exception ignored) { return "notification"; }
    }
    private String safeCode(String value) { return blank(value) ? "PROVIDER_REJECTED" : value.replaceAll("[^A-Za-z0-9_.-]", "_").substring(0, Math.min(64, value.length())); }
    private String text(JsonNode json, String field) { JsonNode value=json.get(field); return value==null || value.isNull() ? null : value.asText(); }
    private Long longValue(JsonNode json, String field) { JsonNode value=json.get(field); return value==null || !value.canConvertToLong() ? null : value.asLong(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
