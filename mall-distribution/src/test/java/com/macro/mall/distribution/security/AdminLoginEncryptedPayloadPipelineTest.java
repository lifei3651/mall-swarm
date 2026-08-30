package com.macro.mall.distribution.security;

import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.PayloadEncryptionService;
import com.macro.mall.distribution.vo.AdminAuthVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLoginEncryptedPayloadPipelineTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PayloadEncryptionService payloadEncryptionService;
    @MockitoBean private AdminAuthService adminAuthService;

    @Test
    void encryptedAdminLoginChallengeIsConsumedOnlyOnceAcrossAdviceAndAspect() throws Exception {
        when(payloadEncryptionService.hasSensitiveValue(any(AdminLoginDTO.class))).thenReturn(true);
        doAnswer(invocation -> {
            AdminLoginDTO decrypted = invocation.getArgument(2);
            decrypted.setPassword("decrypted-password");
            decrypted.setCaptchaCode("A1B2");
            return null;
        }).when(payloadEncryptionService).decryptSensitiveValues(
                eq("one-time-challenge"), eq("encrypted-key"), any(AdminLoginDTO.class));
        AdminAuthVO auth = new AdminAuthVO();
        auth.setToken("test-admin-session-token");
        auth.setExpireTime(LocalDateTime.now().plusHours(12));
        when(adminAuthService.login(any(AdminLoginDTO.class))).thenReturn(auth);

        mockMvc.perform(post("/distribution/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(EncryptedPayloadRequestBodyAdvice.CHALLENGE_HEADER, "one-time-challenge")
                        .header(EncryptedPayloadRequestBodyAdvice.ENCRYPTED_KEY_HEADER, "encrypted-key")
                        .content("""
                                {"username":"security-probe","password":"enc:v1:iv:ciphertext",
                                 "captchaId":"captcha-id","captchaCode":"enc:v1:iv:ciphertext","portal":"PLATFORM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(payloadEncryptionService, times(1)).decryptSensitiveValues(
                eq("one-time-challenge"), eq("encrypted-key"), any(AdminLoginDTO.class));
    }
}
