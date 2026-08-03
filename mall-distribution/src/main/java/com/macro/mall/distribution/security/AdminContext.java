package com.macro.mall.distribution.security;

import com.macro.mall.distribution.entity.DmsAdminUser;

public final class AdminContext {

    private static final ThreadLocal<DmsAdminUser> CURRENT = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(DmsAdminUser admin) {
        CURRENT.set(admin);
    }

    public static DmsAdminUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
