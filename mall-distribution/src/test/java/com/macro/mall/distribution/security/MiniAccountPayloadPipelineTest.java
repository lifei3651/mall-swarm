package com.macro.mall.distribution.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniAccountPayloadPipelineTest {
    @Autowired MockMvc mvc;

    @Test void bothNativeAccountRoutesRejectUnencryptedNestedSecrets() throws Exception {
        for (String route : new String[]{"account-login", "account-register"}) {
            mvc.perform(post("/shop/wechat-mini-program/auth/" + route)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"credentials":{"account":"FixtureAccount","password":"FixtureOnlyPassword"},
                     "privacyAgreed":true,"privacyConsentVersion":"fixture"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("页面安全组件已更新，请刷新页面后重试"));
        }
    }

    @Test void missingCredentialsCannotReachAuthentication() throws Exception {
        mvc.perform(post("/shop/wechat-mini-program/auth/account-login")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
}
