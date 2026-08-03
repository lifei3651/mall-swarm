package com.macro.mall.distribution.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** 商城订单编号生成器：时间可读，唯一性由完整雪花订单ID保证。 */
public final class ShopOrderNoGenerator {

    private static final String PREFIX = "L";
    private static final int ID_TOKEN_LENGTH = 13;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ShopOrderNoGenerator() {
    }

    public static String generate(long orderId, LocalDateTime createTime) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("订单ID必须大于0");
        }
        Objects.requireNonNull(createTime, "订单创建时间不能为空");

        // 正数 long 的36进制编码最多13位；固定补齐后长度稳定，并完整保留ID的唯一性。
        String idToken = Long.toUnsignedString(orderId, 36).toUpperCase(Locale.ROOT);
        String paddedToken = "0".repeat(ID_TOKEN_LENGTH - idToken.length()) + idToken;
        return PREFIX + DATE_TIME.format(createTime) + paddedToken;
    }
}
