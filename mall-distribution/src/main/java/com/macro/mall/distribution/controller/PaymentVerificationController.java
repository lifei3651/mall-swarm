package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.PaymentVerificationService;
import com.macro.mall.distribution.service.ShopAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentVerificationController {

    private final PaymentVerificationService paymentVerificationService;
    private final ShopAuthService shopAuthService;

    @GetMapping("/checkVerify")
    public CommonResult<Map<String, Object>> checkVerify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam BigDecimal amount) {
        shopAuthService.requireMember(authorization);
        return CommonResult.success(paymentVerificationService.getVerificationConfig(amount));
    }
}
