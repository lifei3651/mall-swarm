package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import com.macro.mall.distribution.vo.PayloadEncryptionKeyVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.Mac;

@Service
@RequiredArgsConstructor
public class PayloadEncryptionServiceImpl implements PayloadEncryptionService {

    static final String ENCRYPTED_PREFIX = "enc:v1:";
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "currentpassword", "newpassword", "oldpassword",
            "loginpassword", "paymentpassword", "adminpassword", "confirmpassword",
            "smscode", "captchacode", "appsecret", "callbacktoken", "code"
    );

    private static final String USED_CHALLENGE_KEY_PREFIX = "payload-encryption-used:";
    private static final String ALGORITHM = "RSA-OAEP-256+A256GCM";

    private final StringRedisTemplate redisTemplate;
    private KeyPair keyPair;
    private String publicKey;
    private byte[] challengeSigningKey;

    @PostConstruct
    void initializeKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keyPair = generator.generateKeyPair();
            publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            challengeSigningKey = new byte[32];
            new SecureRandom().nextBytes(challengeSigningKey);
        } catch (Exception exception) {
            throw new IllegalStateException("请求加密密钥初始化失败", exception);
        }
    }

    @Override
    public PayloadEncryptionKeyVO issueChallenge() {
        ensureInitialized();
        long expiresAt = System.currentTimeMillis() + CHALLENGE_TTL.toMillis();
        String unsignedChallenge = UUID.randomUUID().toString().replace("-", "") + "." + expiresAt;
        String challengeId = unsignedChallenge + "." + signChallenge(unsignedChallenge);
        return new PayloadEncryptionKeyVO(
                challengeId,
                publicKey,
                ALGORITHM,
                expiresAt
        );
    }

    @Override
    public boolean hasSensitiveValue(Object body) {
        return !sensitiveFields(body).isEmpty();
    }

    @Override
    public void decryptSensitiveValues(String challengeId, String encryptedKey, Object body) {
        ensureInitialized();
        if (challengeId == null || challengeId.isBlank() || encryptedKey == null || encryptedKey.isBlank()) {
            Asserts.fail("页面安全组件已更新，请刷新页面后重试");
        }

        validateAndConsumeChallenge(challengeId);

        try {
            SecretKey aesKey = decryptAesKey(encryptedKey, keyPair.getPrivate());
            for (SensitiveField sensitiveField : sensitiveFields(body)) {
                String encryptedValue = sensitiveField.value();
                if (!encryptedValue.startsWith(ENCRYPTED_PREFIX)) {
                    Asserts.fail("检测到未加密的敏感信息，请刷新页面后重试");
                }
                String plainText = decryptValue(challengeId, sensitiveField.name(), encryptedValue, aesKey);
                sensitiveField.write(plainText);
            }
        } catch (com.macro.mall.common.exception.ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            Asserts.fail("敏感信息解密失败，请刷新页面后重试");
        }
    }

    private SecretKey decryptAesKey(String encryptedKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec parameters = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, parameters);
        byte[] rawKey = cipher.doFinal(Base64.getDecoder().decode(encryptedKey));
        if (rawKey.length != 32) throw new IllegalArgumentException("invalid AES key length");
        return new SecretKeySpec(rawKey, "AES");
    }

    private void validateAndConsumeChallenge(String challengeId) {
        try {
            String[] parts = challengeId.split("\\.", -1);
            if (parts.length != 3) Asserts.fail("安全请求已失效，请重新提交");
            long expiresAt = Long.parseLong(parts[1]);
            String unsignedChallenge = parts[0] + "." + parts[1];
            byte[] expectedSignature = Base64.getUrlDecoder().decode(signChallenge(unsignedChallenge));
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)
                    || expiresAt < System.currentTimeMillis()
                    || expiresAt > System.currentTimeMillis() + CHALLENGE_TTL.toMillis() + 5000L) {
                Asserts.fail("安全请求已失效，请重新提交");
            }
            String challengeDigest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(challengeId.getBytes(StandardCharsets.UTF_8)));
            Boolean firstUse = redisTemplate.opsForValue().setIfAbsent(
                    USED_CHALLENGE_KEY_PREFIX + challengeDigest, "1", CHALLENGE_TTL);
            if (!Boolean.TRUE.equals(firstUse)) Asserts.fail("安全请求已失效，请重新提交");
        } catch (com.macro.mall.common.exception.ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            Asserts.fail("安全请求已失效，请重新提交");
        }
    }

    private String signChallenge(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(challengeSigningKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("安全请求签名失败", exception);
        }
    }

    private String decryptValue(String challengeId, String fieldName, String encryptedValue,
                                SecretKey aesKey) throws Exception {
        String[] parts = encryptedValue.substring(ENCRYPTED_PREFIX.length()).split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("invalid encrypted value");

        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] cipherText = Base64.getDecoder().decode(parts[1]);
        if (iv.length != 12) throw new IllegalArgumentException("invalid IV length");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad(challengeId, fieldName));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private List<SensitiveField> sensitiveFields(Object body) {
        List<SensitiveField> result = new ArrayList<>();
        collectSensitiveFields(body, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectSensitiveFields(Object value, List<SensitiveField> result) {
        if (value == null) return;
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fieldName = String.valueOf(entry.getKey());
                Object fieldValue = entry.getValue();
                if (isSensitive(fieldName) && fieldValue instanceof String text && !text.isEmpty()) {
                    result.add(new SensitiveField(fieldName, text,
                            plainText -> ((Map<Object, Object>) map).put(entry.getKey(), plainText)));
                } else {
                    collectNested(fieldValue, result);
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectNested(item, result));
            return;
        }
        if (isTerminalType(value.getClass())) return;

        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    if (!field.trySetAccessible()) continue;
                    Object fieldValue = field.get(value);
                    if (isSensitive(field.getName()) && fieldValue instanceof String text && !text.isEmpty()) {
                        result.add(new SensitiveField(field.getName(), text,
                                plainText -> field.set(value, plainText)));
                    } else {
                        collectNested(fieldValue, result);
                    }
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("无法处理安全字段", exception);
                }
            }
            type = type.getSuperclass();
        }
    }

    private void collectNested(Object value, List<SensitiveField> result) {
        if (value == null || isTerminalType(value.getClass())) return;
        collectSensitiveFields(value, result);
    }

    private boolean isSensitive(String fieldName) {
        return SENSITIVE_FIELDS.contains(fieldName.toLowerCase(Locale.ROOT));
    }

    private boolean isTerminalType(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type) || Boolean.class == type
                || java.time.temporal.Temporal.class.isAssignableFrom(type)
                || type.getPackageName().startsWith("java.");
    }

    private byte[] aad(String challengeId, String fieldName) {
        return (challengeId + ":" + fieldName.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8);
    }

    private void ensureInitialized() {
        if (keyPair == null) initializeKeyPair();
    }

    private record SensitiveField(String name, String value, FieldWriter writer) {
        void write(String plainText) throws IllegalAccessException {
            writer.write(plainText);
        }
    }

    @FunctionalInterface
    private interface FieldWriter {
        void write(String plainText) throws IllegalAccessException;
    }
}
