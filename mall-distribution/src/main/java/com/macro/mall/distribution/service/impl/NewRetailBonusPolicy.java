package com.macro.mall.distribution.service.impl;

import java.math.BigDecimal;

/**
 * 历史演示数据使用的新零售样例常量。
 *
 * <p>仅由兼容程序读取，不是商城基座默认制度，也不得用于新客户交付。
 * 每个客户派生项目应实现自己的 CustomerBonusPolicy。</p>
 */
public final class NewRetailBonusPolicy {

    public static final String VERSION_NO = "NEW_RETAIL_SIMPLE_DEFAULT";
    public static final String DIRECT_REWARD = "DIRECT_REWARD";
    public static final String DIRECTOR_SHARE = "DIRECTOR_SHARE";

    private static final BigDecimal[] DIRECT_RATES = {BigDecimal.ZERO, new BigDecimal("0.25"),
            new BigDecimal("0.30"), new BigDecimal("0.37"), new BigDecimal("0.45"),
            new BigDecimal("0.52"), new BigDecimal("0.57"), new BigDecimal("0.61"),
            new BigDecimal("0.65")};
    private static final BigDecimal[] DIRECTOR_SHARE_RATES = {BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.05"),
            new BigDecimal("0.04"), new BigDecimal("0.03"), new BigDecimal("0.02")};

    private NewRetailBonusPolicy() {
    }

    public static BigDecimal directRate(Integer rank) {
        return rateForRank(DIRECT_RATES, rank);
    }

    public static BigDecimal directorShareRate(Integer rank) {
        return rateForRank(DIRECTOR_SHARE_RATES, rank);
    }

    public static BigDecimal maximumDirectorShareRate() {
        return directorShareRate(5).add(directorShareRate(6))
                .add(directorShareRate(7)).add(directorShareRate(8));
    }

    public static BigDecimal maximumTotalPayoutRate() {
        return directRate(8).add(maximumDirectorShareRate());
    }

    private static BigDecimal rateForRank(BigDecimal[] rates, Integer rank) {
        return rank == null || rank < 1 || rank >= rates.length ? BigDecimal.ZERO : rates[rank];
    }
}
