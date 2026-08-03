package com.macro.mall.common.tenant;

/**
 * 租户上下文持有者
 * 使用 ThreadLocal 存储当前请求的租户ID
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        Long tenantId = TENANT_ID.get();
        return tenantId != null ? tenantId : 1L; // 默认租户
    }

    public static Long getCurrentTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
