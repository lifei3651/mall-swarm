package com.macro.mall.common.constant;

/**
 * 权限相关常量定义
 * Created by macro on 2020/6/19.
 */
public final class AuthConstant {

    private AuthConstant() {
        // 防止实例化
    }

    /**
     * JWT存储权限前缀
     */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * JWT存储权限属性
     */
    public static final String AUTHORITY_CLAIM_NAME = "authorities";

    /**
     * 后台管理client_id
     */
    public static final String ADMIN_CLIENT_ID = "admin-app";

    /**
     * 前台商城client_id
     */
    public static final String PORTAL_CLIENT_ID = "portal-app";

    /**
     * 后台管理接口路径匹配
     */
    public static final String ADMIN_URL_PATTERN = "/mall-admin/**";

    /**
     * Redis缓存权限规则（路径->资源）
     */
    public static final String PATH_RESOURCE_MAP = "auth:pathResourceMap";

    /**
     * 认证信息Http请求头
     */
    public static final String JWT_TOKEN_HEADER = "Authorization";

    /**
     * JWT令牌前缀
     */
    public static final String JWT_TOKEN_PREFIX = "Bearer ";

    /**
     * 用户信息Http请求头
     */
    public static final String USER_TOKEN_HEADER = "user";

}
