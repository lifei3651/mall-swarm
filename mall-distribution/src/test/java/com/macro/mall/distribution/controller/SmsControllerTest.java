package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.sms.AliyunSmsSender;
import com.macro.mall.distribution.dto.SmsCodeRequestDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.SmsVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.startsWith;

class SmsControllerTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SmsVerificationService verificationService;
    private ShopAuthService shopAuthService;
    private AliyunSmsSender aliyunSmsSender;
    private SmsController controller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        verificationService = mock(SmsVerificationService.class);
        shopAuthService = mock(ShopAuthService.class);
        aliyunSmsSender = mock(AliyunSmsSender.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        controller = new SmsController(redisTemplate, aliyunSmsSender, verificationService, shopAuthService);
        ReflectionTestUtils.setField(controller, "providerEnabled", false);
        ReflectionTestUtils.setField(controller, "exposeCode", true);
        ReflectionTestUtils.setField(controller, "testCode", "123456");
    }

    @Test
    void sensitiveCodeIsAlwaysSentToCurrentMembersBoundPhone() {
        DmsShopMember member = member("13900000000");
        when(shopAuthService.requireMember("Bearer token")).thenReturn(member);

        SmsCodeRequestDTO dto = request("13888888888", null, 6);
        controller.sendCode(dto, "Bearer token");

        verify(shopAuthService).requireMember("Bearer token");
        verify(valueOperations).set(eq("sms:6:13900000000"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
        verify(valueOperations, never()).set(eq("sms:6:13888888888"), eq("123456"), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void publicCodeUsesRequestedPhoneWithoutRequiringLogin() {
        SmsCodeRequestDTO dto = request("13888888888", null, 1);
        controller.sendCode(dto, null);

        verify(shopAuthService, never()).requireMember(org.mockito.ArgumentMatchers.any());
        verify(valueOperations).set(eq("sms:1:13888888888"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void sensitiveVerificationIgnoresClientSuppliedPhone() {
        when(shopAuthService.requireMember("Bearer token")).thenReturn(member("13900000000"));

        controller.verifyCode(request("13888888888", "654321", 7), "Bearer token");

        verify(verificationService).verifyAndConsume("13900000000", "654321", 7);
    }

    @Test
    void paymentPasswordEndpointUsesServerSideBusinessType() {
        when(shopAuthService.requireMember("Bearer token")).thenReturn(member("13900000000"));

        CommonResult<String> result = controller.sendPaymentPasswordCode("Bearer token");

        assertEquals(200, result.getCode());
        verify(valueOperations).set(eq("sms:7:13900000000"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void unsupportedBusinessTypeIsRejectedBeforeAnySmsAction() {
        CommonResult<String> result = controller.sendCode(request("13888888888", null, 99), null);

        assertNotEquals(200, result.getCode());
        verify(shopAuthService, never()).requireMember(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(valueOperations);
    }

    @Test
    void dailyLimitBlocksExcessiveSends() {
        ReflectionTestUtils.setField(controller, "providerEnabled", true);
        ReflectionTestUtils.setField(controller, "dailyLimitPerPhone", 10);
        when(valueOperations.increment(startsWith("sms:daily:"))).thenReturn(11L);

        CommonResult<String> result = controller.sendCode(request("13888888888", null, 1), null);

        assertNotEquals(200, result.getCode());
        verify(aliyunSmsSender, never()).sendVerificationCode(anyString(), anyInt(), anyString());
    }

    private SmsCodeRequestDTO request(String phone, String code, int bizType) {
        SmsCodeRequestDTO dto = new SmsCodeRequestDTO();
        dto.setPhone(phone);
        dto.setCode(code);
        dto.setBizType(bizType);
        return dto;
    }

    private DmsShopMember member(String phone) {
        DmsShopMember member = new DmsShopMember();
        member.setPhone(phone);
        return member;
    }
}
