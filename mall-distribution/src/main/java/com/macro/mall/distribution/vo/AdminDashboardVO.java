package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 后台控制台实时数据。 */
@Data
public class AdminDashboardVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long memberCount;
    private Long promotionMemberCount;
    private Long registeredMemberCount;
    private Long validMemberCount;
    private Long pendingMemberCount;
    private Long monthNewMemberCount;

    private BigDecimal totalSalesAmount;
    private BigDecimal monthSalesAmount;
    private BigDecimal last7DaysSalesAmount;
    private BigDecimal todaySalesAmount;
    private BigDecimal todayPerformance;
    private BigDecimal yesterdayPerformance;

    private BigDecimal unsettledCommission;
    private Long unsettledCommissionCount;
    private BigDecimal pendingWithdrawAmount;
    private Long pendingWithdrawCount;
    private BigDecimal totalWithdrawAmount;
    private BigDecimal monthWithdrawAmount;

    private BigDecimal totalReceiptAmount;
    private BigDecimal totalPayoutAmount;
    private BigDecimal totalProductCostAmount;
    private BigDecimal totalBonusPayoutAmount;
    private BigDecimal totalCompanyShareAmount;
    private BigDecimal totalProfitAmount;
    /** 利润率，0-1。 */
    private BigDecimal profitRate;

    private Long addressedMemberCount;
    private Long unaddressedMemberCount;
    private List<DashboardProductRankingVO> productRanking;
    private List<DashboardRegionVO> memberRegionDistribution;
    private Long lowStockCount;
    private List<DashboardLowStockVO> lowStockProducts;

    private List<DashboardTrendVO> performanceTrend;
    private List<DashboardTrendVO> monthlyPerformanceTrend;
    private List<DashboardLevelCountVO> levelDistribution;
    private List<DashboardWithdrawVO> pendingWithdraws;
    private List<DashboardCommissionVO> latestCommissions;
}
