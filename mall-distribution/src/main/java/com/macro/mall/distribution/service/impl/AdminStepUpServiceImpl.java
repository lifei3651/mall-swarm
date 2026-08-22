package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.service.AdminStepUpService;
import com.macro.mall.distribution.vo.AdminStepUpTokenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminStepUpServiceImpl implements AdminStepUpService {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public AdminStepUpTokenVO issue(DmsAdminUser admin, String method, String path) {
        if (admin == null || admin.getId() == null) Asserts.fail("后台登录已失效，请重新登录");
        String token = randomToken();
        redisTemplate.opsForValue().set(key(token), binding(admin.getId(), method, path), TTL);
        return new AdminStepUpTokenVO(token, TTL.toSeconds());
    }

    @Override
    public void consume(DmsAdminUser admin, String method, String path, String token) {
        if (admin == null || admin.getId() == null || token == null || token.isBlank() || token.length() > 200) {
            Asserts.fail("没有操作权限：该操作需要再次验证当前管理员密码");
        }
        Long consumed = redisTemplate.execute(CONSUME_SCRIPT, List.of(key(token)),
                binding(admin.getId(), method, path));
        if (!Long.valueOf(1L).equals(consumed)) {
            Asserts.fail("没有操作权限：二次验证已失效或已使用，请重新验证");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String token) {
        return "admin:step-up:" + SecureUtil.sha256(token);
    }

    private String binding(Long adminId, String method, String path) {
        return adminId + ":" + normalizeMethod(method) + ":" + normalizePath(path);
    }

    private String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        int query = path.indexOf('?');
        return (query >= 0 ? path.substring(0, query) : path).trim();
    }
}
