package com.macro.mall.distribution.service;

public interface SmsVerificationService {

    void verifyAndConsume(String phone, String code, Integer bizType);

    /** 生成新验证码后重置该号码在该业务类型下的错误次数。 */
    void resetAttempts(String phone, Integer bizType);
}
