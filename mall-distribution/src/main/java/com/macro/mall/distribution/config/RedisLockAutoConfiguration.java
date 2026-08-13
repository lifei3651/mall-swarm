package com.macro.mall.distribution.config;

import com.macro.mall.common.lock.RedisLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在 Spring Boot 建立 StringRedisTemplate 之后注册公共分布式锁。
 * 使用自动配置顺序，避免普通配置类过早判断 Bean 尚不存在。
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisLock.class)
    public RedisLock redisLock(StringRedisTemplate redisTemplate) {
        return new RedisLock(redisTemplate);
    }
}
