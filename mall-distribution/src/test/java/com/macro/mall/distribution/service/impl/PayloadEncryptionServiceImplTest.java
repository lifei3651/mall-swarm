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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void detectsSensitiveValuesInsideNestedMapsCollectionsAndDtoArrays() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("smsCode", "123456");
        assertTrue(service.hasSensitiveValue(Map.of("rows", new ArrayList<>(List.of(fields)))));
        assertTrue(service.hasSensitiveValue(new NestedBody(List.of(Map.of("credentials", fields)))));
        ShopLoginDTO login = new ShopLoginDTO();
        login.setPassword("synthetic-test-value");
        assertTrue(service.hasSensitiveValue(new NestedBody(new ShopLoginDTO[]{login})));
        assertFalse(service.hasSensitiveValue(new NestedBody(Map.of("rows", List.of(Map.of("quantity", 2, "title", "商品"))))));
        assertFalse(service.hasSensitiveValue(new int[]{1, 2, 3}));
    }

    @Test
    void refusesNestedPlaintextWithOrWithoutEncryptionHeaders() throws Exception {
        Map<String, Object> body = Map.of("rows", List.of(new NestedBody(Map.of("currentPassword", "synthetic-test-value"))));
        assertTrue(service.hasSensitiveValue(body), "Advice/Aspect must see nested fields before controller execution");
        assertThrows(ApiException.class, () -> service.decryptSensitiveValues(null, null, body));
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL))).thenReturn(true);
        assertThrows(ApiException.class, () -> service.decryptSensitiveValues(
                challenge.getChallengeId(), encryptAesKey(aesKey, challenge.getPublicKey()), body));
    }

    @Test
    void decryptsMixedDtoMapListAndArrayUsingTheSameFieldAad() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL))).thenReturn(true);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("SMSCode", encryptValue("123456", "SMSCode", challenge.getChallengeId(), aesKey));
        RealNameVerifyDTO identity = new RealNameVerifyDTO();
        identity.setRealName(encryptValue("测试姓名", "realName", challenge.getChallengeId(), aesKey));
        NestedBody body = new NestedBody(Map.of("rows", List.of(fields, new RealNameVerifyDTO[]{identity})));
        service.decryptSensitiveValues(challenge.getChallengeId(), encryptAesKey(aesKey, challenge.getPublicKey()), body);
        assertEquals("123456", fields.get("SMSCode"));
        assertEquals("测试姓名", identity.getRealName());
    }

    @Test
    void nestedCiphertextCannotBeMovedToADifferentSensitiveField() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL))).thenReturn(true);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("newPassword", encryptValue("synthetic-test-value", "currentPassword", challenge.getChallengeId(), aesKey));
        assertThrows(ApiException.class, () -> service.decryptSensitiveValues(challenge.getChallengeId(),
                encryptAesKey(aesKey, challenge.getPublicKey()), new NestedBody(List.of(fields))));
    }

    @Test
    void merchantReviewCheckCodesDecryptBeforeNestedBeanValidation() throws Exception {
        PayloadEncryptionKeyVO challenge = service.issueChallenge();
        SecretKey aesKey = aesKey();
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(PayloadEncryptionServiceImpl.CHALLENGE_TTL))).thenReturn(true);
        var decision = new com.macro.mall.distribution.dto.MerchantProductReviewDecisionDTO();
        decision.setApproved(true);
        List<com.macro.mall.distribution.dto.MerchantProductReviewCheckDTO> checks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            var check = new com.macro.mall.distribution.dto.MerchantProductReviewCheckDTO();
            check.setCode(encryptValue("CHECK_" + index, "code", challenge.getChallengeId(), aesKey));
            check.setPassed(true); checks.add(check);
        }
        decision.setChecks(checks);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(decision).isEmpty(), "Ciphertext exceeds code length until Advice decrypts it");
            service.decryptSensitiveValues(challenge.getChallengeId(), encryptAesKey(aesKey, challenge.getPublicKey()), decision);
            assertTrue(factory.getValidator().validate(decision).isEmpty());
            assertEquals("CHECK_5", checks.get(5).getCode());
        }
    }

    @Test
    void normalOrderAndProductBodiesPassThroughUnchanged() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var order = json.readValue("{\"addressId\":1,\"payType\":\"WECHAT\",\"items\":[{\"productId\":1,\"skuId\":2,\"quantity\":3}]}",
                com.macro.mall.distribution.dto.ShopOrderSubmitDTO.class);
        var product = json.readValue("{\"product\":{\"productName\":\"测试商品\"},\"skus\":[],\"removedSkuIds\":[1,2]}",
                com.macro.mall.distribution.dto.ProductPublishDTO.class);
        assertFalse(service.hasSensitiveValue(order)); assertFalse(service.hasSensitiveValue(product));
        var servlet = new org.springframework.mock.web.MockHttpServletRequest("POST", "/shop/orders");
        var advice = new com.macro.mall.distribution.security.EncryptedPayloadRequestBodyAdvice(service, servlet);
        var message = new org.springframework.http.server.ServletServerHttpRequest(servlet);
        assertSame(order, advice.afterBodyRead(order, message, null, null, null));
        assertSame(product, advice.afterBodyRead(product, message, null, null, null));
        assertEquals(3, order.getItems().get(0).getQuantity());
    }

    @Test
    void rawCallbackBodiesRemainOpaqueAndErpEncryptionExceptionIsPreserved() {
        byte[] receipt = "{\"code\":\"provider-receipt\"}".getBytes(StandardCharsets.UTF_8);
        var servlet = new org.springframework.mock.web.MockHttpServletRequest("POST", "/shop/notification/receipts/1/SMS/provider");
        var advice = new com.macro.mall.distribution.security.EncryptedPayloadRequestBodyAdvice(service, servlet);
        var message = new org.springframework.http.server.ServletServerHttpRequest(servlet);
        assertFalse(service.hasSensitiveValue(receipt));
        assertSame(receipt, advice.afterBodyRead(receipt, message, null, null, null));
        String wechatRaw = "{\"code\":\"provider-encrypted-content\"}";
        assertFalse(service.hasSensitiveValue(wechatRaw));
        assertSame(wechatRaw, advice.afterBodyRead(wechatRaw, message, null, null, null));
        servlet.setRequestURI("/distribution/erp/callbacks/fixture");
        var erpBody = Map.of("nested", Map.of("callbackToken", "synthetic-provider-token"));
        assertTrue(service.hasSensitiveValue(erpBody));
        assertSame(erpBody, advice.afterBodyRead(erpBody, message, null, null, null));
    }

    private static class NestedBody {
        private final Object nested;
        NestedBody(Object nested) { this.nested = nested; }
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
