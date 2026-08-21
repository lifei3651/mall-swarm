package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.vo.DashboardCommissionVO;
import com.macro.mall.distribution.vo.DashboardFinanceSummaryVO;
import com.macro.mall.distribution.vo.DashboardLevelCountVO;
import com.macro.mall.distribution.vo.DashboardLowStockVO;
import com.macro.mall.distribution.vo.DashboardMonthlyTrendVO;
import com.macro.mall.distribution.vo.DashboardProductRankingVO;
import com.macro.mall.distribution.vo.DashboardRegionVO;
import com.macro.mall.distribution.vo.DashboardTrendVO;
import com.macro.mall.distribution.vo.DashboardWithdrawVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminDashboardDao {
    long countMembers();
    long countPromotionMembers();
    long countNewMembers(@Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);
    BigDecimal sumSales(@Param("tenantId") Long tenantId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);
    BigDecimal sumPerformance(@Param("tenantId") Long tenantId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);
    BigDecimal sumUnsettledCommission(@Param("tenantId") Long tenantId);
    long countUnsettledCommission(@Param("tenantId") Long tenantId);
    BigDecimal sumPendingWithdraw();
    long countPendingWithdraw();
    BigDecimal sumSuccessfulWithdraw(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
    DashboardFinanceSummaryVO selectFinanceSummary(@Param("tenantId") Long tenantId);
    List<DashboardProductRankingVO> selectProductRanking(@Param("tenantId") Long tenantId,
                                                         @Param("limit") int limit);
    long countLowStockProducts(@Param("tenantId") Long tenantId);
    List<DashboardLowStockVO> selectLowStockProducts(@Param("tenantId") Long tenantId,
                                                     @Param("limit") int limit);
    List<DashboardRegionVO> selectMemberRegionDistribution(@Param("tenantId") Long tenantId);
    List<DashboardTrendVO> selectPerformanceTrend(@Param("tenantId") Long tenantId,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);
    List<DashboardMonthlyTrendVO> selectMonthlyPerformanceTrend(@Param("tenantId") Long tenantId,
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime);
    List<DashboardLevelCountVO> selectLevelDistribution();
    List<DashboardWithdrawVO> selectPendingWithdraws(@Param("limit") int limit);
    List<DashboardCommissionVO> selectLatestCommissions(@Param("tenantId") Long tenantId,
                                                        @Param("limit") int limit);
}
