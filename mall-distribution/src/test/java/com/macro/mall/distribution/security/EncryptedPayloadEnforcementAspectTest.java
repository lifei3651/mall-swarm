package com.macro.mall.distribution.security;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.controller.AdminAuthController;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class EncryptedPayloadEnforcementAspectTest {

    @Test
    void rejectsSensitiveControllerBodyWithoutSuccessfulDecryptionMarker() throws Exception {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        EncryptedPayloadEnforcementAspect aspect = new EncryptedPayloadEnforcementAspect(encryptionService, request);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setPassword("plain-password");
        JoinPoint joinPoint = adminLoginJoinPoint(dto);
        when(request.getRequestURI()).thenReturn("/distribution/admin-auth/login");
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        assertThrows(ApiException.class, () -> aspect.requireEncryptedSensitivePayload(joinPoint));
    }

    @Test
    void acceptsSensitiveControllerBodyOnlyAfterAdviceMarkedItDecrypted() throws Exception {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        EncryptedPayloadEnforcementAspect aspect = new EncryptedPayloadEnforcementAspect(encryptionService, request);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setPassword("decrypted-password");
        JoinPoint joinPoint = adminLoginJoinPoint(dto);
        when(request.getRequestURI()).thenReturn("/distribution/admin-auth/login");
        when(request.getAttribute(EncryptedPayloadRequestBodyAdvice.DECRYPTED_PAYLOAD_ATTRIBUTE))
                .thenReturn(Boolean.TRUE);
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        assertDoesNotThrow(() -> aspect.requireEncryptedSensitivePayload(joinPoint));
    }

    @Test
    void decryptsInAspectWhenAdviceWasSkippedButEncryptionHeadersArePresent() throws Exception {
        PayloadEncryptionService encryptionService = mock(PayloadEncryptionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        EncryptedPayloadEnforcementAspect aspect = new EncryptedPayloadEnforcementAspect(encryptionService, request);
        AdminLoginDTO dto = new AdminLoginDTO();
        dto.setPassword("enc:v1:iv:ciphertext");
        JoinPoint joinPoint = adminLoginJoinPoint(dto);
        when(request.getRequestURI()).thenReturn("/distribution/admin-auth/login");
        when(request.getHeader(EncryptedPayloadRequestBodyAdvice.CHALLENGE_HEADER)).thenReturn("challenge");
        when(request.getHeader(EncryptedPayloadRequestBodyAdvice.ENCRYPTED_KEY_HEADER)).thenReturn("encrypted-key");
        when(encryptionService.hasSensitiveValue(dto)).thenReturn(true);

        assertDoesNotThrow(() -> aspect.requireEncryptedSensitivePayload(joinPoint));
        verify(encryptionService).decryptSensitiveValues("challenge", "encrypted-key", dto);
        verify(request).setAttribute(EncryptedPayloadRequestBodyAdvice.DECRYPTED_PAYLOAD_ATTRIBUTE, Boolean.TRUE);
    }

    private JoinPoint adminLoginJoinPoint(AdminLoginDTO dto) throws Exception {
        Method method = AdminAuthController.class.getMethod("login", AdminLoginDTO.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{dto});
        return joinPoint;
    }
}
