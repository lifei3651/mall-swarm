package com.macro.mall.distribution.config;

import com.macro.mall.common.aspect.IdempotentAspect;
import com.macro.mall.common.idempotency.IdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 在持久幂等存储准备完成后装配接口幂等切面。
 *
 * <p>公共模块不在分销应用的默认组件扫描路径内，不能只依赖
 * {@code @Component}；否则单元测试能直接实例化切面，而正式运行时
 * 提交订单、余额支付、转账和提现均缺少数据库持久幂等拦截。</p>
 */
@AutoConfiguration
@ConditionalOnBean(IdempotencyStore.class)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotentAspect.class)
    public IdempotentAspect idempotentAspect(IdempotencyStore idempotencyStore) {
        return new IdempotentAspect(idempotencyStore);
    }
}
