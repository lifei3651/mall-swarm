package com.macro.mall.distribution.controller;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.PaymentVerificationService;
import com.macro.mall.distribution.service.ShopAuthService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentVerificationControllerSecurityTest {

    @Test
    void anonymousRequestCannotReadPaymentVerificationPolicy() {
        PaymentVerificationService verificationService = mock(PaymentVerificationService.class);
        ShopAuthService authService = mock(ShopAuthService.class);
        when(authService.requireMember(null)).thenThrow(new ApiException("请先登录"));
        PaymentVerificationController controller = new PaymentVerificationController(verificationService, authService);

        assertThrows(ApiException.class,
                () -> controller.checkVerify(null, new BigDecimal("500")));

        verifyNoInteractions(verificationService);
    }

    @Test
    void authenticatedMemberCanStillCheckCurrentPaymentAmount() {
        PaymentVerificationService verificationService = mock(PaymentVerificationService.class);
        ShopAuthService authService = mock(ShopAuthService.class);
        when(authService.requireMember("Bearer valid")).thenReturn(new DmsShopMember());
        when(verificationService.getVerificationConfig(new BigDecimal("500")))
                .thenReturn(Map.of("needVerify", true));
        PaymentVerificationController controller = new PaymentVerificationController(verificationService, authService);

        assertEquals(Boolean.TRUE,
                controller.checkVerify("Bearer valid", new BigDecimal("500")).getData().get("needVerify"));
        verify(authService).requireMember("Bearer valid");
    }
}
