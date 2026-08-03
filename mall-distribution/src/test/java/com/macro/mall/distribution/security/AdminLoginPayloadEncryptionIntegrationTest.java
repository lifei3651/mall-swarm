package com.macro.mall.distribution.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLoginPayloadEncryptionIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void actualMvcPipelineRejectsPlaintextAdminCredentials() throws Exception {
        mockMvc.perform(post("/distribution/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"security-probe","password":"plain-password",
                                 "captchaId":"captcha-id","captchaCode":"A7K9"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("页面安全组件已更新，请刷新页面后重试"));
    }
}
