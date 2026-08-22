package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.vo.AdminStepUpTokenVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminStepUpServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void issuedTokenIsShortLivedAndBoundForAtomicSingleUse() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AdminStepUpServiceImpl service = new AdminStepUpServiceImpl(redis);
        DmsAdminUser admin = admin(12L);

        AdminStepUpTokenVO issued = service.issue(admin, "post", "/distribution/withdraw/audit");

        assertEquals(120L, issued.getExpiresInSeconds());
        assertTrue(issued.getToken().length() >= 40);
        verify(values).set(anyString(), anyString(), any(Duration.class));

        when(redis.execute(any(), anyList(), anyString())).thenReturn(1L);
        assertDoesNotThrow(() -> service.consume(admin, "POST", "/distribution/withdraw/audit", issued.getToken()));
    }

    @Test
    void rejectsMissingExpiredReplayedOrWronglyBoundToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyString())).thenReturn(0L);
        AdminStepUpServiceImpl service = new AdminStepUpServiceImpl(redis);

        ApiException missing = assertThrows(ApiException.class,
                () -> service.consume(admin(7L), "PUT", "/shop/admin/orders/8/cancel", null));
        assertTrue(missing.getMessage().contains("需要再次验证"));
        assertEquals(ResultCode.FORBIDDEN, missing.getErrorCode());

        ApiException invalid = assertThrows(ApiException.class,
                () -> service.consume(admin(7L), "PUT", "/shop/admin/orders/8/cancel", "wrong-token"));
        assertTrue(invalid.getMessage().contains("已失效或已使用"));
    }

    private DmsAdminUser admin(Long id) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        return admin;
    }
}
