package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dto.RealNameVerifyDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.vo.PayloadEncryptionKeyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayloadEncryptionServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PayloadEncryptionServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new PayloadEncryptionServiceImpl(redisTemplate);
        service.initializeKeyPair();
    }

    @Test
    void decryptsBrowserCompatibleHybridPayloadAndRejectsReplay() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();

        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setAccount("member-account");
        dto.setPassword(encryptValue("safe-password", "password", challenge.getChallengeId(), aesKey));
        dto.setCaptchaCode(encryptValue("A7K9", "captchaCode", challenge.getChallengeId(), aesKey));

        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL)))
                .thenReturn(true)
                .thenReturn(false);

        String encryptedKey = encryptAesKey(aesKey, challenge.getPublicKey());
        service.decryptSensitiveValues(challenge.getChallengeId(), encryptedKey, dto);

        assertEquals("safe-password", dto.getPassword());
        assertEquals("A7K9", dto.getCaptchaCode());

        dto.setPassword(encryptValue("safe-password", "password", challenge.getChallengeId(), aesKey));
        assertThrows(ApiException.class,
                () -> service.decryptSensitiveValues(challenge.getChallengeId(), encryptedKey, dto));
    }

    @Test
    void refusesPlaintextSensitiveFieldEvenWithAChallenge() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL)))
                .thenReturn(true);

        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setPassword("plain-password");

        assertThrows(ApiException.class, () -> service.decryptSensitiveValues(
                challenge.getChallengeId(), encryptAesKey(aesKey, challenge.getPublicKey()), dto));
    }

    @Test
    void decryptsRealNameAndIdentityNumberAsSensitiveFields() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL)))
                .thenReturn(true);

        RealNameVerifyDTO dto = new RealNameVerifyDTO();
        dto.setRealName(encryptValue("张三", "realName", challenge.getChallengeId(), aesKey));
        dto.setIdCard(encryptValue("11010519491231002X", "idCard", challenge.getChallengeId(), aesKey));
        dto.setSensitiveInfoConsent(true);

        service.decryptSensitiveValues(challenge.getChallengeId(), encryptAesKey(aesKey, challenge.getPublicKey()), dto);

        assertEquals("张三", dto.getRealName());
        assertEquals("11010519491231002X", dto.getIdCard());
    }

    private SecretKey aesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private String encryptAesKey(SecretKey aesKey, String publicKeyBase64) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(cipher.doFinal(aesKey.getEncoded()));
    }

    private String encryptValue(String value, String fieldName, String challengeId, SecretKey aesKey) throws Exception {
        byte[] iv = new byte[12];
        java.security.SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
        cipher.updateAAD((challengeId + ":" + fieldName.toLowerCase()).getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return PayloadEncryptionServiceImpl.ENCRYPTED_PREFIX
                + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(encrypted);
    }
}
