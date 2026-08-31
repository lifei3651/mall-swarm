package com.macro.mall.distribution.util;

import java.util.Locale;

/** 商城业务编号生成器：使用完整雪花ID的36进制编码，兼顾短小与全局唯一。 */
public final class ShopOrderNoGenerator {

    private static final int ID_TOKEN_LENGTH = 13;

    private ShopOrderNoGenerator() {
    }

    public static String generate(long orderId) {
        return generate("L", orderId, "订单ID必须大于0");
    }

    public static String generateTrade(long tradeId) {
        return generate("T", tradeId, "联合支付ID必须大于0");
    }

    private static String generate(String prefix, long id, String invalidMessage) {
        if (id <= 0) throw new IllegalArgumentException(invalidMessage);
        // 正数long的36进制编码最多13位；固定补齐且完整保留雪花ID，不使用截断或随机取模。
        String idToken = Long.toUnsignedString(id, 36).toUpperCase(Locale.ROOT);
        String paddedToken = "0".repeat(ID_TOKEN_LENGTH - idToken.length()) + idToken;
        return prefix + paddedToken;
    }
}
