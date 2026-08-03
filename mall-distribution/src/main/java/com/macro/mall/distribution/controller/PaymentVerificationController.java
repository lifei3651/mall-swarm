package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.PaymentVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentVerificationController {

    private final PaymentVerificationService paymentVerificationService;

    @GetMapping("/checkVerify")
    public CommonResult<Map<String, Object>> checkVerify(@RequestParam BigDecimal amount) {
        return CommonResult.success(paymentVerificationService.getVerificationConfig(amount));
    }
}
