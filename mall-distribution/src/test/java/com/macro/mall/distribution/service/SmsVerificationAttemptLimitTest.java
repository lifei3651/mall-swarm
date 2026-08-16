package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.service.impl.SmsVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码错误次数限制测试：锁定错误尝试，但不允许第三方使受害人的正确验证码失效。
 */
@ExtendWith(MockitoExtension.class)
class SmsVerificationAttemptLimitTest {

    private static final String PHONE = "13900000000";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private SmsVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SmsVerificationServiceImpl(redisTemplate);
    }

    @Test
    void wrongCodeFiveTimesLocksFurtherWrongAttemptsWithoutInvalidatingVictimsCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(), anyList(), eq("000000"))).thenReturn(-1L);
        when(valueOperations.get("sms:attempt:3:" + PHONE)).thenReturn(null);
        when(valueOperations.increment("sms:attempt:3:" + PHONE))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 1; i <= 4; i++) {
            ApiException error = assertThrows(ApiException.class,
                    () -> service.verifyAndConsume(PHONE, "000000", 3));
            assertEquals("验证码错误", error.getMessage());
        }

        ApiException locked = assertThrows(ApiException.class,
                () -> service.verifyAndConsume(PHONE, "000000", 3));
        assertEquals("验证码错误次数过多，请稍后再试", locked.getMessage());

        verify(redisTemplate, never()).delete("sms:3:" + PHONE);
        verify(redisTemplate).expire(eq("sms:attempt:3:" + PHONE), eq(5L), eq(TimeUnit.MINUTES));

        when(redisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);
        service.verifyAndConsume(PHONE, "123456", 3);
    }

    @Test
    void correctCodeConsumesAndClearsAttempts() {
        when(redisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

        service.verifyAndConsume(PHONE, "123456", 3);

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void expiredOrMissingCodeIsRejected() {
        when(redisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(0L);

        ApiException error = assertThrows(ApiException.class,
                () -> service.verifyAndConsume(PHONE, "123456", 3));
        assertEquals("验证码不存在或已过期", error.getMessage());
    }

    @Test
    void resetAttemptsClearsCounter() {
        service.resetAttempts(PHONE, 3);
        verify(redisTemplate).delete("sms:attempt:3:" + PHONE);
    }
}
