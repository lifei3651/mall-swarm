package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsVerificationServiceImpl implements SmsVerificationService {

    private static final String SMS_CODE_KEY_PREFIX = "sms:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void verifyAndConsume(String phone, String code, Integer bizType) {
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) Asserts.fail("会员手机号不正确");
        if (code == null || !code.matches("^\\d{6}$")) Asserts.fail("请输入6位短信验证码");
        String key = SMS_CODE_KEY_PREFIX + (bizType == null ? 1 : bizType) + ":" + phone;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) Asserts.fail("验证码不存在或已过期");
        if (!cachedCode.equals(code)) Asserts.fail("验证码错误");
        redisTemplate.delete(key);
    }
}
