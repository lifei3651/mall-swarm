package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SmsVerificationServiceImpl implements SmsVerificationService {

    private static final String SMS_CODE_KEY_PREFIX = "sms:";
    private static final String SMS_ATTEMPT_KEY_PREFIX = "sms:attempt:";
    private static final int SMS_CODE_EXPIRE_MINUTES = 5;
    /** 单个验证码允许的试错次数，防止 6 位验证码被暴力枚举。 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> VERIFY_AND_CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local actual = redis.call('GET', KEYS[1])
            if not actual then
              return 0
            end
            if actual ~= ARGV[1] then
              return -1
            end
            redis.call('DEL', KEYS[1])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void verifyAndConsume(String phone, String code, Integer bizType) {
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) Asserts.fail("会员手机号不正确");
        if (code == null || !code.matches("^\\d{6}$")) Asserts.fail("请输入6位短信验证码");
        int normalizedBizType = bizType == null ? 1 : bizType;
        String key = SMS_CODE_KEY_PREFIX + normalizedBizType + ":" + phone;
        String attemptKey = SMS_ATTEMPT_KEY_PREFIX + normalizedBizType + ":" + phone;
        Long consumeResult = redisTemplate.execute(VERIFY_AND_CONSUME_SCRIPT, List.of(key, attemptKey), code);
        if (Long.valueOf(1L).equals(consumeResult)) {
            return;
        }
        if (consumeResult == null || consumeResult == 0L) {
            Asserts.fail("验证码不存在或已过期");
        }
        if (consumeResult == -1L) {
            String previousAttempts = redisTemplate.opsForValue().get(attemptKey);
            if (previousAttempts != null && Integer.parseInt(previousAttempts) >= MAX_VERIFY_ATTEMPTS) {
                Asserts.fail("验证码错误次数过多，请稍后再试");
            }
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(attemptKey, SMS_CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
            if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                Asserts.fail("验证码错误次数过多，请稍后再试");
            }
            Asserts.fail("验证码错误");
        }
        Asserts.fail("验证码校验失败，请重新获取");
    }

    @Override
    public void resetAttempts(String phone, Integer bizType) {
        if (phone == null || phone.isBlank()) return;
        redisTemplate.delete(SMS_ATTEMPT_KEY_PREFIX + (bizType == null ? 1 : bizType) + ":" + phone);
    }
}
