package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.AdminDashboardDao;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import com.macro.mall.distribution.service.AdminDashboardService;
import com.macro.mall.distribution.vo.AdminDashboardVO;
import com.macro.mall.distribution.vo.DashboardFinanceSummaryVO;
import com.macro.mall.distribution.vo.DashboardLevelCountVO;
import com.macro.mall.distribution.vo.DashboardMonthlyTrendVO;
import com.macro.mall.distribution.vo.DashboardProductRankingVO;
import com.macro.mall.distribution.vo.DashboardRegionVO;
import com.macro.mall.distribution.vo.DashboardTrendVO;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardDao dashboardDao;

    @Override
    public AdminDashboardVO getDashboard() {
        Long tenantId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime last7DaysStart = today.minusDays(6).atStartOfDay();
        LocalDate trendStart = today.minusDays(29);
        LocalDate monthlyTrendStart = today.withDayOfMonth(1).minusMonths(11);

        AdminDashboardVO vo = new AdminDashboardVO();
        long registeredMembers = dashboardDao.countMembers();
        long validMembers = dashboardDao.countPromotionMembers();
        vo.setMemberCount(registeredMembers);
        vo.setPromotionMemberCount(validMembers);
        vo.setRegisteredMemberCount(registeredMembers);
        vo.setValidMemberCount(validMembers);
        vo.setPendingMemberCount(Math.max(registeredMembers - validMembers, 0));
        vo.setMonthNewMemberCount(dashboardDao.countNewMembers(monthStart, tomorrowStart));

        BigDecimal todaySales = zero(dashboardDao.sumSales(tenantId, todayStart, tomorrowStart));
        vo.setTotalSalesAmount(zero(dashboardDao.sumSales(tenantId, null, null)));
        vo.setMonthSalesAmount(zero(dashboardDao.sumSales(tenantId, monthStart, tomorrowStart)));
        vo.setLast7DaysSalesAmount(zero(dashboardDao.sumSales(tenantId, last7DaysStart, tomorrowStart)));
        vo.setTodaySalesAmount(todaySales);
        vo.setTodayPerformance(todaySales);
        vo.setYesterdayPerformance(zero(dashboardDao.sumPerformance(tenantId, yesterdayStart, todayStart)));

        vo.setUnsettledCommission(zero(dashboardDao.sumUnsettledCommission()));
        vo.setUnsettledCommissionCount(dashboardDao.countUnsettledCommission());
        vo.setPendingWithdrawAmount(zero(dashboardDao.sumPendingWithdraw()));
        vo.setPendingWithdrawCount(dashboardDao.countPendingWithdraw());
        vo.setTotalWithdrawAmount(zero(dashboardDao.sumSuccessfulWithdraw(null, null)));
        vo.setMonthWithdrawAmount(zero(dashboardDao.sumSuccessfulWithdraw(monthStart, tomorrowStart)));

        DashboardFinanceSummaryVO finance = dashboardDao.selectFinanceSummary(tenantId);
        BigDecimal receipts = finance == null ? BigDecimal.ZERO : zero(finance.getTotalReceiptAmount());
        BigDecimal payouts = finance == null ? BigDecimal.ZERO : zero(finance.getTotalPayoutAmount());
        BigDecimal profit = finance == null ? BigDecimal.ZERO : zero(finance.getTotalProfitAmount());
        vo.setTotalReceiptAmount(receipts);
        vo.setTotalPayoutAmount(payouts);
        vo.setTotalProductCostAmount(finance == null ? BigDecimal.ZERO : zero(finance.getTotalProductCostAmount()));
        vo.setTotalBonusPayoutAmount(finance == null ? BigDecimal.ZERO : zero(finance.getTotalBonusPayoutAmount()));
        vo.setTotalCompanyShareAmount(finance == null ? BigDecimal.ZERO : zero(finance.getTotalCompanyShareAmount()));
        vo.setTotalProfitAmount(profit);
        vo.setProfitRate(receipts.compareTo(BigDecimal.ZERO) > 0
                ? profit.divide(receipts, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        List<DashboardProductRankingVO> productRanking = dashboardDao.selectProductRanking(tenantId, 10);
        for (int index = 0; index < productRanking.size(); index++) {
            DashboardProductRankingVO row = productRanking.get(index);
            row.setRanking(index + 1);
            row.setSalesAmount(zero(row.getSalesAmount()));
        }
        vo.setProductRanking(productRanking);
        vo.setLowStockCount(dashboardDao.countLowStockProducts(tenantId));
        vo.setLowStockProducts(dashboardDao.selectLowStockProducts(tenantId, 6));

        List<DashboardRegionVO> regions = dashboardDao.selectMemberRegionDistribution(tenantId);
        long addressedMembers = regions.stream()
                .map(DashboardRegionVO::getMemberCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        for (DashboardRegionVO region : regions) {
            long regionMembers = region.getMemberCount() == null ? 0L : region.getMemberCount();
            region.setPercentage(addressedMembers > 0
                    ? BigDecimal.valueOf(regionMembers).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(addressedMembers), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
        vo.setAddressedMemberCount(addressedMembers);
        vo.setUnaddressedMemberCount(Math.max(registeredMembers - addressedMembers, 0));
        vo.setMemberRegionDistribution(regions);

        vo.setPerformanceTrend(fillTrend(trendStart, today,
                dashboardDao.selectPerformanceTrend(tenantId, trendStart.atStartOfDay(), tomorrowStart)));
        vo.setMonthlyPerformanceTrend(fillMonthlyTrend(monthlyTrendStart, today.withDayOfMonth(1),
                dashboardDao.selectMonthlyPerformanceTrend(tenantId, monthlyTrendStart.atStartOfDay(), tomorrowStart)));
        vo.setLevelDistribution(fillLevels(dashboardDao.selectLevelDistribution()));
        var pendingWithdraws = dashboardDao.selectPendingWithdraws(5);
        pendingWithdraws.forEach(row -> {
            String rawAccountName = row.getAccountName();
            if (java.util.Objects.equals(row.getAgentName(), rawAccountName)) {
                row.setAgentName(MemberAccountUtils.maskPersonName(row.getAgentName()));
            }
            row.setAccountName(MemberAccountUtils.maskPersonName(rawAccountName));
        });
        vo.setPendingWithdraws(pendingWithdraws);
        vo.setLatestCommissions(dashboardDao.selectLatestCommissions(5));
        return vo;
    }

    private List<DashboardTrendVO> fillTrend(LocalDate start, LocalDate end, List<DashboardTrendVO> rows) {
        Map<LocalDate, BigDecimal> values = new LinkedHashMap<>();
        for (DashboardTrendVO row : rows) values.put(row.getStatDate(), zero(row.getPerformanceAmount()));
        return start.datesUntil(end.plusDays(1))
                .map(date -> new DashboardTrendVO(date, values.getOrDefault(date, BigDecimal.ZERO)))
                .toList();
    }

    private List<DashboardTrendVO> fillMonthlyTrend(LocalDate start, LocalDate end,
                                                    List<DashboardMonthlyTrendVO> rows) {
        Map<LocalDate, BigDecimal> values = new LinkedHashMap<>();
        for (DashboardMonthlyTrendVO row : rows) {
            if (row.getStatYear() != null && row.getStatMonth() != null) {
                values.put(LocalDate.of(row.getStatYear(), row.getStatMonth(), 1), zero(row.getPerformanceAmount()));
            }
        }
        return start.datesUntil(end.plusMonths(1), java.time.Period.ofMonths(1))
                .map(date -> new DashboardTrendVO(date, values.getOrDefault(date, BigDecimal.ZERO)))
                .toList();
    }

    private List<DashboardLevelCountVO> fillLevels(List<DashboardLevelCountVO> rows) {
        Map<Integer, Long> values = new LinkedHashMap<>();
        for (DashboardLevelCountVO row : rows) values.put(row.getAgentLevel(), row.getMemberCount());
        return java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(level -> {
            AgentLevelEnum levelEnum = AgentLevelEnum.getByValue(level);
            return new DashboardLevelCountVO(level, levelEnum == null ? "未知" : levelEnum.getName(),
                    values.getOrDefault(level, 0L));
        }).toList();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
