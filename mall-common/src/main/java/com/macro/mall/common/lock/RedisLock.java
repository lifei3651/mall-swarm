package com.macro.mall.common.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLock {
    private final StringRedisTemplate redisTemplate;

    /**
     * Lua脚本：原子性地比较并删除锁
     * KEYS[1] = 锁的key
     * ARGV[1] = 期望的value（锁持有者的标识）
     * 返回1表示成功释放，0表示锁已被他人获取或不存在
     */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del', KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";

    private final DefaultRedisScript<Long> unlockScript;

    public RedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    /**
     * 尝试获取锁
     * @param key 锁的key
     * @param value 锁持有者的唯一标识
     * @param timeout 过期时间
     * @param unit 时间单位
     * @return 是否成功获取锁
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    /**
     * 释放锁（原子操作，使用Lua脚本保证比较和删除的原子性）
     * @param key 锁的key
     * @param value 锁持有者的唯一标识
     * @return 是否成功释放
     */
    public boolean unlock(String key, String value) {
        Long result = redisTemplate.execute(unlockScript, Collections.singletonList(key), value);
        return result != null && result == 1L;
    }
}
