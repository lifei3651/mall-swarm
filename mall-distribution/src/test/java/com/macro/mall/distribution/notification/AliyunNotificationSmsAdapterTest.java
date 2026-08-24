package com.macro.mall.distribution.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AliyunNotificationSmsAdapterTest {
    @Test
    void notificationTemplatesAreIndependentAndOnlyLowFrequencyServiceEventsPass() {
        ExternalNotificationProperties external=new ExternalNotificationProperties(); external.setEnabled(true); external.setWorkerEnabled(true);
        AliyunNotificationSmsProperties sms=new AliyunNotificationSmsProperties(); sms.setEnabled(true); sms.setAccessKeyId("notification-ak");
        sms.setAccessKeySecret("notification-secret"); sms.setSignName("服务通知签名"); sms.setReceiptSecret("receipt-secret-123456");
        sms.setTemplates(Map.of("ORDER_SHIPPED","SMS_APPROVED_SHIPPED","WALLET_FLOW","SMS_FORBIDDEN_WALLET"));
        AliyunNotificationSmsAdapter adapter=new AliyunNotificationSmsAdapter(sms,external,new ObjectMapper());
        ExternalNotificationContext shipped=context("ORDER_SHIPPED");
        assertTrue(adapter.readiness(shipped).allowed());
        assertEquals("EVENT_NOT_ALLOWED_FOR_SMS",adapter.readiness(context("WALLET_FLOW")).code());
        assertFalse(sms.getTemplates().containsKey("login"));
        assertFalse(sms.getTemplates().containsKey("verification"));
    }

    @Test
    void forgedOrStaleReceiptIsRejectedBeforeParsing() throws Exception {
        ExternalNotificationProperties external=new ExternalNotificationProperties();
        AliyunNotificationSmsProperties sms=new AliyunNotificationSmsProperties(); sms.setReceiptSecret("receipt-secret-123456");
        AliyunNotificationSmsAdapter adapter=new AliyunNotificationSmsAdapter(sms,external,new ObjectMapper());
        byte[] body="{\"receiptId\":\"r-1\",\"taskId\":1,\"status\":\"DELIVERED\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp=String.valueOf(Instant.now().getEpochSecond());
        String valid=sign(sms.getReceiptSecret(),timestamp,body);
        assertTrue(adapter.verifyReceipt(Map.of("x-notification-timestamp",timestamp,"x-notification-signature",valid),body));
        assertFalse(adapter.verifyReceipt(Map.of("x-notification-timestamp",timestamp,"x-notification-signature","00".repeat(32)),body));
        assertFalse(adapter.verifyReceipt(Map.of("x-notification-timestamp",String.valueOf(Instant.now().minusSeconds(600).getEpochSecond()),"x-notification-signature",valid),body));
        assertEquals("DELIVERED",adapter.parseReceipt(body).status());
    }
    private ExternalNotificationContext context(String event) { ExternalNotificationContext value=new ExternalNotificationContext();value.setEventType(event);value.setPhone("13900000001");return value; }
    private String sign(String secret,String timestamp,byte[] body) throws Exception { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(mac.doFinal(body)); }
}
