package com.macro.mall.common.sms;

import java.util.Set;

/**
 * 商城短信业务类型的统一定义，避免前后端分别维护数字常量导致类型错配。
 */
public final class SmsBusinessType {
    public static final int REGISTER = 1;
    public static final int LOGIN = 2;
    public static final int RESET_PASSWORD = 3;
    public static final int TRANSFER = 4;
    public static final int WITHDRAW = 5;
    public static final int PAYMENT = 6;
    public static final int SET_PAYMENT_PASSWORD = 7;
    public static final int RESET_LOGIN_PASSWORD = 8;

    public static final Set<Integer> SUPPORTED = Set.of(
            REGISTER, LOGIN, RESET_PASSWORD, TRANSFER,
            WITHDRAW, PAYMENT, SET_PAYMENT_PASSWORD, RESET_LOGIN_PASSWORD);

    private SmsBusinessType() {
    }
}
