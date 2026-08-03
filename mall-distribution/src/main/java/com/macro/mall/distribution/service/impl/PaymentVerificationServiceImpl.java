package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.PaymentVerificationService;
import com.macro.mall.distribution.service.SmsVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentVerificationServiceImpl implements PaymentVerificationService {

    private static final int PAYMENT_SMS_BIZ_TYPE = 6;

    private final SmsVerificationService smsVerificationService;

    @Value("${payment.verification.enabled:false}")
    private boolean enabled;

    @Value("${payment.verification.threshold:500}")
    private BigDecimal threshold;

    @Override
    public Map<String, Object> getVerificationConfig(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        BigDecimal safeThreshold = threshold == null ? new BigDecimal("500") : threshold.max(BigDecimal.ZERO);
        boolean needVerify = enabled && safeAmount.compareTo(safeThreshold) >= 0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("threshold", safeThreshold);
        result.put("needVerify", needVerify);
        result.put("message", needVerify ? "本次支付金额较大，需要短信验证" : "");
        return result;
    }

    @Override
    public void verifyIfRequired(DmsShopMember member, BigDecimal amount, String smsCode) {
        if (!Boolean.TRUE.equals(getVerificationConfig(amount).get("needVerify"))) return;
        if (member == null || member.getPhone() == null) Asserts.fail("当前登录会员手机号不存在");
        smsVerificationService.verifyAndConsume(member.getPhone(), smsCode, PAYMENT_SMS_BIZ_TYPE);
    }
}
