package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsVerificationServiceImpl implements SmsVerificationService {

    private static final String SMS_CODE_KEY_PREFIX = "sms:";
    private static final String SMS_ATTEMPT_KEY_PREFIX = "sms:attempt:";
    private static final int SMS_CODE_EXPIRE_MINUTES = 5;
    /** 单个验证码允许的试错次数，防止 6 位验证码被暴力枚举。 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void verifyAndConsume(String phone, String code, Integer bizType) {
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) Asserts.fail("会员手机号不正确");
        if (code == null || !code.matches("^\\d{6}$")) Asserts.fail("请输入6位短信验证码");
        int normalizedBizType = bizType == null ? 1 : bizType;
        String key = SMS_CODE_KEY_PREFIX + normalizedBizType + ":" + phone;
        String attemptKey = SMS_ATTEMPT_KEY_PREFIX + normalizedBizType + ":" + phone;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) Asserts.fail("验证码不存在或已过期");
        if (!cachedCode.equals(code)) {
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(attemptKey, SMS_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
            if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                redisTemplate.delete(key);
                Asserts.fail("验证码错误次数过多，请重新获取");
            }
            Asserts.fail("验证码错误");
        }
        redisTemplate.delete(key);
        redisTemplate.delete(attemptKey);
    }

    @Override
    public void resetAttempts(String phone, Integer bizType) {
        if (phone == null || phone.isBlank()) return;
        redisTemplate.delete(SMS_ATTEMPT_KEY_PREFIX + (bizType == null ? 1 : bizType) + ":" + phone);
    }
}
