package com.macro.mall.common.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.macro.mall.common.exception.Asserts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunSmsSender {
    private static final Map<Integer, String> TEMPLATE_KEYS = Map.of(
            SmsBusinessType.REGISTER, "register",
            SmsBusinessType.LOGIN, "login",
            SmsBusinessType.RESET_PASSWORD, "reset-password",
            SmsBusinessType.TRANSFER, "transfer",
            SmsBusinessType.WITHDRAW, "withdraw",
            SmsBusinessType.PAYMENT, "payment",
            SmsBusinessType.SET_PAYMENT_PASSWORD, "payment-password",
            SmsBusinessType.RESET_LOGIN_PASSWORD, "reset-password",
            SmsBusinessType.CHANGE_PHONE_CURRENT, "verification",
            SmsBusinessType.CHANGE_PHONE_NEW, "verification");
    private final AliyunSmsProperties properties;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public void sendVerificationCode(String phone, Integer bizType, String code) {
        validate(phone, bizType);
        String templateCode = resolveTemplateCode(bizType);
        if (isBlank(templateCode)) {
            Asserts.fail("短信模板未配置");
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client().sendSms(request);
            if (response == null || response.getBody() == null || !"OK".equals(response.getBody().getCode())) {
                String message = response == null || response.getBody() == null ? "无响应" : response.getBody().getMessage();
                log.warn("阿里云短信发送失败: phoneSuffix={}, message={}", mask(phone), message);
                Asserts.fail("短信发送失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("阿里云短信调用异常: phoneSuffix={}", mask(phone), e);
            Asserts.fail("短信发送失败，请稍后重试");
        }
    }

    public static boolean supportsBusinessType(Integer bizType) {
        return bizType != null && TEMPLATE_KEYS.containsKey(bizType);
    }

    private Client client() throws Exception {
        String key = properties.getAccessKeyId() + "@" + properties.getEndpoint();
        Client existing = clients.get(key);
        if (existing != null) return existing;
        Config config = new Config().setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret()).setEndpoint(properties.getEndpoint());
        Client created = new Client(config);
        clients.put(key, created);
        return created;
    }

    private void validate(String phone, Integer bizType) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) Asserts.fail("手机号格式不正确");
        if (!supportsBusinessType(bizType)) Asserts.fail("短信业务类型不支持");
        if (isBlank(properties.getAccessKeyId()) || isBlank(properties.getAccessKeySecret()) || isBlank(properties.getSignName())) {
            Asserts.fail("短信服务未配置");
        }
    }

    private String resolveTemplateCode(Integer bizType) {
        String templateCode = properties.getTemplates().get(TEMPLATE_KEYS.get(bizType));
        if (isBlank(templateCode)) templateCode = properties.getTemplates().get("verification");
        if (isBlank(templateCode)) templateCode = properties.getTemplates().get("login");
        return templateCode;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String mask(String phone) { return phone == null || phone.length() < 4 ? "****" : phone.substring(phone.length() - 4); }
}
