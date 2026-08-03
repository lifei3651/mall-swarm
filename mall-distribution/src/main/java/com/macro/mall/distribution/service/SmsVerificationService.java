package com.macro.mall.distribution.service;

public interface SmsVerificationService {

    void verifyAndConsume(String phone, String code, Integer bizType);
}
