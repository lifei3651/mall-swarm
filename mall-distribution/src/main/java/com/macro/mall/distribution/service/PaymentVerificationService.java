package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopMember;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentVerificationService {

    Map<String, Object> getVerificationConfig(BigDecimal amount);

    void verifyIfRequired(DmsShopMember member, BigDecimal amount, String smsCode);
}
