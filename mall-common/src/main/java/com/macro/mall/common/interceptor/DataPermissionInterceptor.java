package com.macro.mall.common.interceptor;

import com.macro.mall.common.annotation.DataScope;
import com.macro.mall.common.tenant.TenantContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 数据权限拦截器
 * 基于租户ID实现行级数据隔离
 */
@Aspect
@Component
public class DataPermissionInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataPermissionInterceptor.class);

    /**
     * 在方法执行前设置租户上下文
     * 后续 MyBatis 拦截器可以根据 TenantContext 自动添加 tenant_id 条件
     */
    @Before("@annotation(dataScope)")
    public void doBefore(JoinPoint point, DataScope dataScope) {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            LOGGER.warn("数据权限拦截: 租户ID为空，使用默认租户");
            TenantContext.setTenantId(1L);
        }

        // 记录数据权限审计日志
        String className = point.getTarget().getClass().getSimpleName();
        String methodName = point.getSignature().getName();
        LOGGER.debug("数据权限拦截: {}.{}() tenantId={}", className, methodName, TenantContext.getTenantId());
    }
}
