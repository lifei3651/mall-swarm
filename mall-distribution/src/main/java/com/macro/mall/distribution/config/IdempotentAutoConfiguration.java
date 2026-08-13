package com.macro.mall.distribution.config;

import com.macro.mall.common.aspect.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在正式应用的 Redis 客户端准备完成后装配接口幂等切面。
 *
 * <p>公共模块不在分销应用的默认组件扫描路径内，不能只依赖
 * {@code @Component}；否则单元测试能直接实例化切面，而正式运行时
 * 提交订单、余额支付、转账和提现均缺少 Redis 第一层重复请求拦截。</p>
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentAspect.class)
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate) {
        return new IdempotentAspect(redisTemplate);
    }
}
