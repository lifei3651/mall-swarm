package com.macro.mall.distribution.util;

import com.macro.mall.common.exception.Asserts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 资金入口的统一精度与数据库边界校验。 */
public final class MoneyValidationUtils {

    private MoneyValidationUtils() {
    }

    public static BigDecimal requirePositiveAmount(BigDecimal amount, String name, BigDecimal maximum) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail(name + "必须大于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            Asserts.fail(name + "最多保留2位小数");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (maximum != null && normalized.compareTo(maximum) > 0) {
            Asserts.fail(name + "不能超过" + maximum.toPlainString() + "元");
        }
        return normalized;
    }
}
