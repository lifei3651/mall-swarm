package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.PaymentVerificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentVerificationServiceTest {

    @Test
    void verificationIsEnforcedByServerAtConfiguredThreshold() {
        SmsVerificationService sms = mock(SmsVerificationService.class);
        PaymentVerificationServiceImpl service = new PaymentVerificationServiceImpl(sms);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "threshold", new BigDecimal("500"));
        DmsShopMember member = new DmsShopMember();
        member.setPhone("13900000000");

        assertFalse((Boolean) service.getVerificationConfig(new BigDecimal("499.99")).get("needVerify"));
        assertTrue((Boolean) service.getVerificationConfig(new BigDecimal("500")).get("needVerify"));
        service.verifyIfRequired(member, new BigDecimal("500"), "123456");
        verify(sms).verifyAndConsume("13900000000", "123456", 6);
    }

    @Test
    void disabledVerificationDoesNotConsumeSmsCode() {
        SmsVerificationService sms = mock(SmsVerificationService.class);
        PaymentVerificationServiceImpl service = new PaymentVerificationServiceImpl(sms);
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "threshold", new BigDecimal("500"));

        service.verifyIfRequired(new DmsShopMember(), new BigDecimal("9999"), null);
        verifyNoInteractions(sms);
    }
}
