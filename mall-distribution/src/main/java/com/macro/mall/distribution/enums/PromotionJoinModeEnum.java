package com.macro.mall.distribution.enums;

import java.util.Locale;

/**
 * 客户级推广资格开通方式。
 *
 * 邀请关系只记录“谁邀请了谁”，本枚举只决定未开通账号何时获得推广资格；
 * 客户奖金名称、层级、比例和计算公式仍由客户独立项目实现。
 */
public enum PromotionJoinModeEnum {

    /** 不自动开通；新客户基座的安全默认值。 */
    DISABLED,

    /** 通过邀请链接或二维码注册后立即开通。 */
    AUTO_ON_INVITE,

    /** 只允许后台审核后开通。 */
    MANUAL_REVIEW,

    /** 完成首笔有效支付订单后开通；仅用于兼容已有商城。 */
    FIRST_PAID_ORDER;

    public static PromotionJoinModeEnum forExisting(String value) {
        if (value == null || value.isBlank()) return FIRST_PAID_ORDER;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("推广资格开通方式不正确");
        }
    }

    public static PromotionJoinModeEnum forNew(String value) {
        if (value == null || value.isBlank()) return DISABLED;
        return forExisting(value);
    }

    public boolean autoOnInvite() {
        return this == AUTO_ON_INVITE;
    }

    public boolean autoOnPaidOrder() {
        return this == FIRST_PAID_ORDER;
    }
}
