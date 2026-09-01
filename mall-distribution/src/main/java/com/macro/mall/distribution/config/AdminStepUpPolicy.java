package com.macro.mall.distribution.config;

import org.springframework.http.HttpMethod;

/**
 * 需要重新验证当前管理员密码的账号安全与团队关系写操作。
 *
 * 日常经营动作由菜单权限、业务状态机、精准确认和操作日志保护，避免后台人员
 * 在售后、提现、结算等连续工作中反复输入登录密码。人工增减会员余额使用各自
 * DTO 内的当前管理员密码校验，不经过本策略。
 */
final class AdminStepUpPolicy {

    static final String HEADER = "X-Admin-Step-Up-Token";

    private AdminStepUpPolicy() {
    }

    static boolean requires(String method, String path) {
        if (path == null) return false;
        if (HttpMethod.PUT.matches(method)
                && path.matches("/distribution/admin-users/[^/]+/(status|unlock)")) return true;
        return HttpMethod.POST.matches(method)
                && (path.equals("/distribution/agent/switch-line")
                || path.matches("/distribution/agent/line-change-applications/[^/]+/audit"));
    }
}
