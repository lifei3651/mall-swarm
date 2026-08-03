package com.macro.mall.distribution.service.impl;

import java.math.BigDecimal;

/**
 * 当前商城唯一生效的新零售奖金口径。
 *
 * <p>比例固化在代码中，后台不能新增、编辑或切换其他奖金规则，避免旧版三级分佣与正式方案串用。</p>
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
