package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncryptedPayloadRequestBodyAdviceTest {

    @Test
    void shopClientPlaintextPasswordIsRejectedBeforeController() {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/shop/auth/login");
        EncryptedPayloadRequestBodyAdvice advice = new EncryptedPayloadRequestBodyAdvice(
                encryptionService, servletRequest);
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setPassword("plain-password");
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        assertThrows(ApiException.class,
                () -> advice.afterBodyRead(dto, request, null, null, null));
        verify(encryptionService, never()).decryptSensitiveValues(null, null, dto);
    }

    @Test
    void requestWithoutSensitiveValuesPassesThroughUntouched() {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/shop/orders");
        EncryptedPayloadRequestBodyAdvice advice = new EncryptedPayloadRequestBodyAdvice(
                encryptionService, servletRequest);
        Object body = new Object();
        when(encryptionService.hasSensitiveValue(body)).thenReturn(false);

        Object result = advice.afterBodyRead(body,
                new ServletServerHttpRequest(servletRequest),
                null, null, null);

        assertSame(body, result);
    }

    @Test
    void successfulDecryptionMarksRequestForControllerEnforcement() {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/shop/auth/login");
        EncryptedPayloadRequestBodyAdvice advice = new EncryptedPayloadRequestBodyAdvice(
                encryptionService, servletRequest);
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setPassword("enc:v1:iv:ciphertext");
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        servletRequest.addHeader(EncryptedPayloadRequestBodyAdvice.CHALLENGE_HEADER, "challenge");
        servletRequest.addHeader(EncryptedPayloadRequestBodyAdvice.ENCRYPTED_KEY_HEADER, "encrypted-key");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        assertSame(dto, advice.afterBodyRead(dto, request, null, null, null));
        verify(encryptionService).decryptSensitiveValues("challenge", "encrypted-key", dto);
        assertSame(Boolean.TRUE, servletRequest.getAttribute(
                EncryptedPayloadRequestBodyAdvice.DECRYPTED_PAYLOAD_ATTRIBUTE));
    }

    @Test
    void servletHeadersAreUsedWhenMessageWrapperDropsCustomHeaders() {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/shop/auth/login");
        servletRequest.addHeader(EncryptedPayloadRequestBodyAdvice.CHALLENGE_HEADER, "challenge");
        servletRequest.addHeader(EncryptedPayloadRequestBodyAdvice.ENCRYPTED_KEY_HEADER, "encrypted-key");
        EncryptedPayloadRequestBodyAdvice advice = new EncryptedPayloadRequestBodyAdvice(
                encryptionService, servletRequest);
        ShopLoginDTO dto = new ShopLoginDTO();
        dto.setPassword("enc:v1:iv:ciphertext");
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        HttpInputMessage wrapperWithoutCustomHeaders = new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };

        assertSame(dto, advice.afterBodyRead(dto, wrapperWithoutCustomHeaders, null, null, null));
        verify(encryptionService).decryptSensitiveValues("challenge", "encrypted-key", dto);
    }
}
