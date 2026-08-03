package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.OrderCompanyShareDTO;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.OrderFinanceDTO;
import com.macro.mall.distribution.dto.PerformanceViewPermissionDTO;
import com.macro.mall.distribution.dto.PerformanceVisibilityDTO;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsFinanceRiskRule;
import com.macro.mall.distribution.vo.*;

import java.time.LocalDate;
import java.util.List;

public interface DistributionAuditService {

    DistributionSettingsVO getSettings();

    DistributionSettingsVO updateVisibility(PerformanceVisibilityDTO dto);

    PerformanceViewPermissionVO savePermission(PerformanceViewPermissionDTO dto);

    boolean deletePermission(Long id);

    boolean canViewTeamPerformance(Long agentId, Long userId);

    List<OrderAuditVO> getAllOrders();

    List<OrderAuditVO> getOrdersByMemberKey(String memberKey);

    List<OrderAuditVO> getOrdersByOrderNo(String orderNo);

    List<OrderAuditVO> getOrdersByAgentId(Long agentId);

    List<OrderAuditVO> getOrdersByUserId(Long userId);

    List<CommissionRecordVO> getBonusSourcesByAgentId(Long agentId);

    List<CommissionRecordVO> getBonusSourcesByUserId(Long userId);

    List<CommissionRecordVO> getAllBonusSources();

    List<CommissionRecordVO> getBonusSourcesByMemberKey(String memberKey);

    List<CommissionRecordVO> getBonusSourcesByOrderNo(String orderNo);

    PersonProfileVO getPersonProfile(Long agentId, Long userId, String keyword);

    default PersonProfileVO getPersonProfile(Long agentId, Long userId) {
        return getPersonProfile(agentId, userId, null);
    }

    OrderFinanceDetailVO getOrderFinanceDetail(Long orderId);

    OrderFinanceVO upsertOrderFinance(OrderFinanceDTO dto);

    List<OrderCompanyShareVO> saveCompanyShares(Long orderId, List<OrderCompanyShareDTO> shares);

    void refreshOrderFinance(Long orderId, String orderNo, java.math.BigDecimal payAmount);

    FinanceSummaryVO getFinanceSummary(String range, LocalDate startDate, LocalDate endDate);

    List<FinanceDailySummaryVO> getFinanceDailySummary(String range, LocalDate startDate, LocalDate endDate);

    DmsFinanceRefund saveRefund(FinanceRefundDTO dto);

    List<DmsFinanceRefund> getRefundsByOrderId(Long orderId);

    List<CompanyShareSummaryVO> getCompanyShareSummary(String range, LocalDate startDate, LocalDate endDate);

    List<DmsFinanceRiskRule> listRiskRules();

    DmsFinanceRiskRule saveRiskRule(DmsFinanceRiskRule rule);

    List<FinanceRiskAlertVO> getRiskAlerts(String range, LocalDate startDate, LocalDate endDate);
}
