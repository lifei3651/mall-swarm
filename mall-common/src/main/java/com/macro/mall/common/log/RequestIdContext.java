package com.macro.mall.common.log;

import org.slf4j.MDC;

/**
 * 当前请求的全链路关联号。
 *
 * <p>该编号用于关联接口响应、服务端运行日志和后台业务操作日志，
 * 不等同于数据库业务主键，也不承载用户身份或其他敏感信息。</p>
 */
public final class RequestIdContext {

    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            MDC.remove(MDC_KEY);
            return;
        }
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
