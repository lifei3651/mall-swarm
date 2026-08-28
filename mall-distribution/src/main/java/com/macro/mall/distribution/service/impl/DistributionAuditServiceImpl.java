package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.constants.BalanceAsset;
import com.macro.mall.distribution.bonus.CustomerBonusPolicy;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyRegistry;
import com.macro.mall.distribution.bonus.CustomerBonusRefundContext;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.OrderCompanyShareDTO;
import com.macro.mall.distribution.dto.OrderFinanceDTO;
import com.macro.mall.distribution.dto.PerformanceViewPermissionDTO;
import com.macro.mall.distribution.dto.PerformanceVisibilityDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.vo.*;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DistributionAuditServiceImpl implements DistributionAuditService {

    private static final String TEAM_VISIBLE_ALL = "TEAM_PERFORMANCE_VISIBLE_ALL";
    private static final String DIRECT_SALES_MODE = "DIRECT_SALES_MODE";

    private final DmsDistributionSettingDao settingDao;
    private final DmsPerformanceViewPermissionDao permissionDao;
    private final DmsOrderFinanceDao financeDao;
    private final DmsOrderCompanyShareDao companyShareDao;
    private final DmsFinanceRefundDao refundDao;
    private final DmsFinanceRiskRuleDao riskRuleDao;
    private final DmsCommissionClawbackDao clawbackDao;
    private final DmsCommissionRecordDao commissionRecordDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsOrderPerformanceDetailDao performanceDetailDao;
    private final DmsShopOrderDao shopOrderDao;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao shopMemberDao;
    private final DmsWithdrawRecordDao withdrawRecordDao;
    private final DmsMemberAssetAccountDao memberAssetAccountDao;
    private final DmsMemberAssetFlowDao memberAssetFlowDao;
    private final DmsOrderBalanceAllocationDao orderBalanceAllocationDao;
    private final PerformanceService performanceService;
    private final MemberAssetService memberAssetService;
    private final CustomerBonusPolicyRegistry bonusPolicyRegistry;

    @Override
    public DistributionSettingsVO getSettings() {
        DistributionSettingsVO vo = new DistributionSettingsVO();
        vo.setTeamPerformanceVisibleAll(getBooleanSetting(TEAM_VISIBLE_ALL, true));
        vo.setDirectSalesMode(getBooleanSetting(DIRECT_SALES_MODE, true));
        vo.setPermissions(permissionDao.selectAll().stream().map(this::toPermissionVO).toList());
        return vo;
    }

    @Override
    public DistributionSettingsVO updateVisibility(PerformanceVisibilityDTO dto) {
        boolean visibleAll = dto.getTeamPerformanceVisibleAll() == null || dto.getTeamPerformanceVisibleAll();
        saveSetting(TEAM_VISIBLE_ALL, String.valueOf(visibleAll), "团队业绩是否默认所有代理可见");
        return getSettings();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PerformanceViewPermissionVO savePermission(PerformanceViewPermissionDTO dto) {
        if (dto.getMemberKey() != null && !dto.getMemberKey().isBlank()) {
            DmsAgent resolved = agentDao.selectById(performanceService.resolveAgentId(dto.getMemberKey()));
            dto.setAgentId(resolved.getId());
            dto.setUserId(resolved.getUserId());
        }
        if (dto.getAgentId() == null && dto.getUserId() == null) {
            Asserts.fail("请输入登录账号或手机号");
        }

        DmsAgent agent = dto.getAgentId() != null ? agentDao.selectById(dto.getAgentId()) : agentDao.selectByUserId(dto.getUserId());
        DmsPerformanceViewPermission permission = agent != null
                ? permissionDao.selectByAgentId(agent.getId())
                : (dto.getUserId() != null ? permissionDao.selectByUserId(dto.getUserId()) : null);
        if (permission == null) {
            permission = new DmsPerformanceViewPermission();
        }

        permission.setAgentId(agent != null ? agent.getId() : dto.getAgentId());
        permission.setUserId(agent != null ? agent.getUserId() : dto.getUserId());
        permission.setAgentName(agent != null ? agent.getAgentName() : dto.getAgentName());
        permission.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        permission.setRemark(dto.getRemark());

        if (permission.getId() == null) {
            permissionDao.insert(permission);
        } else {
            permissionDao.update(permission);
        }
        return toPermissionVO(permission);
    }

    @Override
    public boolean deletePermission(Long id) {
        return permissionDao.deleteById(id) > 0;
    }

    @Override
    public boolean canViewTeamPerformance(Long agentId, Long userId) {
        if (getBooleanSetting(TEAM_VISIBLE_ALL, true)) {
            return true;
        }
        DmsPerformanceViewPermission permission = agentId != null
                ? permissionDao.selectByAgentId(agentId)
                : permissionDao.selectByUserId(userId);
        return permission != null && Integer.valueOf(1).equals(permission.getEnabled());
    }

    @Override
    public List<OrderAuditVO> getAllOrders() {
        return buildShopOrderAuditList(shopOrderDao.selectList(null, null, null));
    }

    @Override
    public List<OrderAuditVO> getOrdersByMemberKey(String memberKey) {
        DmsShopMember member = resolveProfileMember(null, memberKey);
        if (member == null) {
            Asserts.fail("未找到对应会员，请使用登录账号或手机号查询");
        }
        return buildShopOrderAuditList(shopOrderDao.selectByUserId(member.getUserId()));
    }

    @Override
    public List<OrderAuditVO> getOrdersByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return Collections.emptyList();
        DmsShopOrder order = shopOrderDao.selectByOrderNo(orderNo.trim());
        return order == null ? Collections.emptyList() : buildShopOrderAuditList(List.of(order));
    }

    @Override
    public List<OrderAuditVO> getOrdersByAgentId(Long agentId) {
        List<DmsShopOrder> orders = shopOrderDao.selectByAgentId(agentId);
        long orderTotal = orders instanceof Page<?> page ? page.getTotal() : orders.size();
        return orderTotal == 0
                ? buildOrderAuditList(performanceDetailDao.selectPersonalPerformanceDetails(agentId, null, null))
                : buildShopOrderAuditList(orders);
    }

    @Override
    public List<OrderAuditVO> getOrdersByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<DmsShopOrder> orders = shopOrderDao.selectByUserId(userId);
        long orderTotal = orders instanceof Page<?> page ? page.getTotal() : orders.size();
        if (orderTotal > 0) return buildShopOrderAuditList(orders);
        DmsAgent agent = agentDao.selectByUserId(userId);
        return agent == null ? Collections.emptyList()
                : buildOrderAuditList(performanceDetailDao.selectPersonalPerformanceDetails(agent.getId(), null, null));
    }

    @Override
    public List<CommissionRecordVO> getBonusSourcesByAgentId(Long agentId) {
        return mapCommissionRecords(commissionRecordDao.selectByAgentId(agentId));
    }

    @Override
    public List<CommissionRecordVO> getBonusSourcesByUserId(Long userId) {
        DmsAgent agent = agentDao.selectByUserId(userId);
        return agent == null ? Collections.emptyList() : getBonusSourcesByAgentId(agent.getId());
    }

    @Override
    public List<CommissionRecordVO> getAllBonusSources() {
        return mapCommissionRecords(commissionRecordDao.selectAll());
    }

    @Override
    public List<CommissionRecordVO> getBonusSourcesByMemberKey(String memberKey) {
        DmsShopMember member = resolveProfileMember(null, memberKey);
        if (member == null) {
            Asserts.fail("未找到对应会员，请使用登录账号或手机号查询");
        }
        return getBonusSourcesByUserId(member.getUserId());
    }

    @Override
    public List<CommissionRecordVO> getBonusSourcesByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return Collections.emptyList();
        return mapCommissionRecords(commissionRecordDao.selectByOrderNo(orderNo.trim()));
    }

    @Override
    public PersonProfileVO getPersonProfile(Long agentId, Long userId, String keyword) {
        DmsShopMember member = resolveProfileMember(userId, keyword);
        DmsAgent agent = agentId != null
                ? agentDao.selectById(agentId)
                : agentDao.selectByUserId(member != null ? member.getUserId() : userId);
        if (agent == null && member == null) {
            Asserts.fail("未找到对应会员，请使用登录账号或手机号查询");
        }
        if (member == null) member = shopMemberDao.selectByUserId(agent.getUserId());

        PersonProfileVO vo = new PersonProfileVO();
        vo.setMember(member);
        vo.setAgent(agent);
        if (agent == null) {
            vo.setAccount(null);
            vo.setPendingDebtAmount(BigDecimal.ZERO);
            vo.setOrders(Collections.emptyList());
            vo.setCommissions(Collections.emptyList());
            vo.setClawbacks(Collections.emptyList());
            DmsMemberAssetAccount balance = memberAssetAccountDao
                    .selectByUserIdAndAssetCode(member.getUserId(), BalanceAsset.CODE);
            vo.setAssetAccounts(balance == null ? Collections.emptyList() : List.of(balance));
            vo.setAssetFlows(memberAssetFlowDao.selectByUserId(member.getUserId(), null));
            vo.setWithdraws(Collections.emptyList());
        } else {
            vo.setAccount(accountDao.selectByAgentId(agent.getId()));
            vo.setPendingDebtAmount(nullToZero(clawbackDao.sumDebtByAgentId(agent.getId())));
            vo.setOrders(getOrdersByAgentId(agent.getId()));
            vo.setCommissions(getBonusSourcesByAgentId(agent.getId()));
            vo.setClawbacks(clawbackDao.selectByAgentId(agent.getId()));
            vo.setAssetAccounts(memberAssetAccountDao.selectByAgentId(agent.getId()));
            vo.setAssetFlows(memberAssetFlowDao.selectByAgentId(agent.getId(), null));
            vo.setWithdraws(withdrawRecordDao.selectByAgentId(agent.getId()));
        }
        return vo;
    }

    private DmsShopMember resolveProfileMember(Long userId, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return shopMemberDao.selectByAccount(keyword.trim());
        }
        return userId == null ? null : shopMemberDao.selectByUserId(userId);
    }

    @Override
    public OrderFinanceDetailVO getOrderFinanceDetail(Long orderId) {
        DmsOrderFinance finance = ensureFinance(orderId, null, null);
        OrderFinanceDetailVO vo = new OrderFinanceDetailVO();
        vo.setFinance(toFinanceVO(finance));
        vo.setBonusFlows(commissionRecordDao.selectByOrderId(orderId).stream().map(this::toCommissionVO).toList());
        vo.setCompanyShares(companyShareDao.selectByOrderId(orderId).stream().map(this::toCompanyShareVO).toList());
        vo.setRefunds(refundDao.selectByOrderId(orderId));
        vo.setClawbacks(clawbackDao.selectByOrderId(orderId));
        List<DmsOrderBalanceAllocation> allocations = orderBalanceAllocationDao.selectByOrderId(orderId);
        allocations.forEach(item -> item.setTargetAccount(
                MemberAccountUtils.display(shopMemberDao.selectById(item.getTargetMemberId()))));
        vo.setBalanceAllocations(allocations);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderFinanceVO upsertOrderFinance(OrderFinanceDTO dto) {
        if (dto.getOrderId() == null) {
            Asserts.fail("订单ID不能为空");
        }
        DmsOrderFinance finance = ensureFinance(dto.getOrderId(), dto.getOrderNo(), dto.getPayAmount());
        DmsShopOrder paidOrder = shopOrderDao.selectById(dto.getOrderId());
        if (paidOrder != null && paidOrder.getStatus() != null && paidOrder.getStatus() >= 1
                && paidOrder.getStatus() <= 4) {
            boolean payChanged = dto.getPayAmount() != null
                    && dto.getPayAmount().compareTo(nullToZero(finance.getPayAmount())) != 0;
            boolean costChanged = dto.getProductCost() != null
                    && dto.getProductCost().compareTo(nullToZero(finance.getProductCost())) != 0;
            if (payChanged || costChanged) {
                Asserts.fail("订单支付后实付金额和冻结产品成本不可修改，请通过退款/售后流程冲账");
            }
        }
        if (dto.getOrderNo() != null) {
            finance.setOrderNo(dto.getOrderNo());
        }
        if (dto.getPayAmount() != null) {
            finance.setPayAmount(dto.getPayAmount());
        }
        if (dto.getProductCost() != null) {
            finance.setProductCost(dto.getProductCost());
        }
        finance.setRemark(dto.getRemark());
        recalculate(finance);
        financeDao.update(finance);
        return toFinanceVO(finance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderCompanyShareVO> saveCompanyShares(Long orderId, List<OrderCompanyShareDTO> shares) {
        Asserts.fail("人工公司分账已停用：产品成本和剩余商品款由系统自动进入对应内部资金账户");
        return List.of();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshOrderFinance(Long orderId, String orderNo, BigDecimal payAmount) {
        DmsOrderFinance finance = ensureFinance(orderId, orderNo, payAmount);
        recalculate(finance);
        financeDao.update(finance);
    }

    @Override
    public FinanceSummaryVO getFinanceSummary(String range, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(range, startDate, endDate);
        LocalDateTime startTime = dateRange.startTime();
        LocalDateTime endTime = dateRange.endTime();
        FinanceSummaryVO summary = financeDao.selectSummary(startTime, endTime);
        if (summary == null) {
            summary = new FinanceSummaryVO();
        }
        fillSummaryDefaults(summary);
        BigDecimal denominator = summary.getNetPayAmount().compareTo(BigDecimal.ZERO) > 0
                ? summary.getNetPayAmount()
                : summary.getPayAmount();
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            summary.setProfitRate(summary.getCompanyProfit().divide(denominator, 4, RoundingMode.HALF_UP));
            summary.setPayoutRate(summary.getBonusAmount().divide(denominator, 4, RoundingMode.HALF_UP));
        } else {
            summary.setProfitRate(BigDecimal.ZERO);
            summary.setPayoutRate(BigDecimal.ZERO);
        }
        return summary;
    }

    @Override
    public List<FinanceDailySummaryVO> getFinanceDailySummary(String range, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(range, startDate, endDate);
        List<FinanceDailySummaryVO> summaries = financeDao.selectDailySummary(dateRange.startTime(), dateRange.endTime());
        for (FinanceDailySummaryVO summary : summaries) {
            if (summary.getOrderCount() == null) {
                summary.setOrderCount(0L);
            }
            if (summary.getRiskOrderCount() == null) {
                summary.setRiskOrderCount(0L);
            }
            summary.setPayAmount(nullToZero(summary.getPayAmount()));
            summary.setRefundAmount(nullToZero(summary.getRefundAmount()));
            summary.setNetPayAmount(nullToZero(summary.getNetPayAmount()));
            summary.setProductCost(nullToZero(summary.getProductCost()));
            summary.setBonusAmount(nullToZero(summary.getBonusAmount()));
            summary.setCompanyShareAmount(nullToZero(summary.getCompanyShareAmount()));
            summary.setCompanyProfit(nullToZero(summary.getCompanyProfit()));
        }
        return summaries;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsFinanceRefund saveRefund(FinanceRefundDTO dto) {
        if (dto == null || dto.getOrderId() == null) {
            Asserts.fail("订单ID不能为空");
        }
        BigDecimal productRefund = nullToZero(dto.getProductRefundAmount());
        BigDecimal freightRefund = nullToZero(dto.getFreightRefundAmount());
        int refundQuantity = dto.getRefundQuantity() == null ? 0 : dto.getRefundQuantity();
        if (productRefund.compareTo(BigDecimal.ZERO) <= 0 || refundQuantity <= 0) {
            Asserts.fail("退款必须包含实际退回的商品及数量");
        }
        if (freightRefund.compareTo(BigDecimal.ZERO) < 0) Asserts.fail("运费退款不能小于0");
        BigDecimal refundTotal = productRefund.add(freightRefund).setScale(2, RoundingMode.HALF_UP);
        DmsOrderFinance finance = ensureFinance(dto.getOrderId(), dto.getOrderNo(), null);
        BigDecimal payAmount = nullToZero(finance.getPayAmount());
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单实付金额异常，不能登记退款");
        }
        // 所有退款登记统一锁定订单，避免并发冲销重复扣减业绩、件数和奖金。
        DmsShopOrder shopOrder = shopOrderDao.selectByIdForUpdate(dto.getOrderId());
        if (shopOrder == null) Asserts.fail("商城订单不存在，不能登记无商品明细退款");
        BigDecimal productAmount = shopOrder.getTotalAmount() == null
                ? payAmount.subtract(nullToZero(shopOrder.getFreightAmount()))
                : nullToZero(shopOrder.getTotalAmount());
        BigDecimal productBase = productAmount.subtract(nullToZero(shopOrder.getDiscountAmount())).max(BigDecimal.ZERO);
        if (productBase.compareTo(BigDecimal.ZERO) <= 0) Asserts.fail("订单商品实付金额异常，不能冲销业绩");

        boolean preciseBonusScope = dto.getBonusRefundAmount() != null
                || dto.getBonusRefundQuantity() != null
                || dto.getBonusBaseAmount() != null
                || dto.getCumulativeBonusRefundAmount() != null;
        BigDecimal bonusRefund = preciseBonusScope ? nullToZero(dto.getBonusRefundAmount()) : productRefund;
        int bonusRefundQuantity = preciseBonusScope
                ? (dto.getBonusRefundQuantity() == null ? 0 : dto.getBonusRefundQuantity())
                : refundQuantity;
        BigDecimal bonusBase = preciseBonusScope ? nullToZero(dto.getBonusBaseAmount()) : productBase;
        BigDecimal cumulativeBonusRefund = preciseBonusScope
                ? nullToZero(dto.getCumulativeBonusRefundAmount()) : null;
        if (bonusRefund.compareTo(BigDecimal.ZERO) < 0 || bonusRefund.compareTo(productRefund) > 0) {
            Asserts.fail("奖金商品退款金额不正确");
        }
        if (bonusRefundQuantity < 0 || bonusRefundQuantity > refundQuantity) {
            Asserts.fail("奖金商品退款数量不正确");
        }
        if (bonusRefund.compareTo(BigDecimal.ZERO) > 0 && bonusBase.compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单奖金商品基数异常，不能冲销奖金");
        }
        if (preciseBonusScope && (cumulativeBonusRefund.compareTo(bonusRefund) < 0
                || cumulativeBonusRefund.compareTo(bonusBase) > 0)) {
            Asserts.fail("奖金商品累计退款金额不正确");
        }

        BigDecimal productRefunded = nullToZero(refundDao.sumProductByOrderId(dto.getOrderId()));
        BigDecimal freightRefunded = nullToZero(refundDao.sumFreightByOrderId(dto.getOrderId()));
        BigDecimal productRefundable = productBase.subtract(productRefunded).max(BigDecimal.ZERO);
        BigDecimal freightRefundable = nullToZero(shopOrder.getFreightAmount()).subtract(freightRefunded).max(BigDecimal.ZERO);
        if (productRefund.compareTo(productRefundable) > 0) {
            Asserts.fail("商品退款超过可退商品金额，可退金额：" + productRefundable);
        }
        boolean shipped = shopOrder.getDeliveryTime() != null || Integer.valueOf(2).equals(shopOrder.getStatus())
                || Integer.valueOf(3).equals(shopOrder.getStatus()) || Integer.valueOf(4).equals(shopOrder.getStatus());
        if (shipped && freightRefund.compareTo(BigDecimal.ZERO) > 0) {
            Asserts.fail("订单已经发货，原发货运费不可退款");
        }
        if (freightRefund.compareTo(freightRefundable) > 0) {
            Asserts.fail("运费退款超过可退运费，可退运费：" + freightRefundable);
        }
        BigDecimal alreadyRefunded = nullToZero(refundDao.sumByOrderId(dto.getOrderId()));
        if (refundTotal.compareTo(payAmount.subtract(alreadyRefunded).max(BigDecimal.ZERO)) > 0) {
            Asserts.fail("退款总额超过订单剩余可退金额");
        }
        DmsFinanceRefund refund = new DmsFinanceRefund();
        refund.setOrderId(dto.getOrderId());
        refund.setOrderNo(dto.getOrderNo() == null ? finance.getOrderNo() : dto.getOrderNo());
        refund.setRefundNo(dto.getRefundNo());
        refund.setRefundAmount(refundTotal);
        refund.setProductRefundAmount(productRefund);
        refund.setFreightRefundAmount(freightRefund);
        refund.setRefundQuantity(refundQuantity);
        refund.setClawbackBonus(dto.getClawbackBonus() == null ? 1 : dto.getClawbackBonus());
        refund.setReason(dto.getReason());
        refund.setOperatorId(dto.getOperatorId());
        refund.setOperatorName(dto.getOperatorName());
        refund.setRefundTime(dto.getRefundTime() == null ? LocalDateTime.now() : dto.getRefundTime());
        if (refundDao.insert(refund) != 1) {
            Asserts.fail("退款订单不属于当前租户");
        }

        // 商品退款按实际退回数量精确冲减；运费永远不参与业绩、件数和奖金冲销。
        performanceService.reverseOrderPerformance(refund.getOrderId(), refund.getId(),
                bonusRefund, bonusRefundQuantity, refund.getRefundTime());
        BigDecimal cumulativeProductRefundRate = preciseBonusScope
                ? (bonusBase.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                    : cumulativeBonusRefund.min(bonusBase).divide(bonusBase, 8, RoundingMode.HALF_UP))
                : productRefunded.add(productRefund).min(productBase)
                    .divide(productBase, 8, RoundingMode.HALF_UP);
        if (Integer.valueOf(1).equals(refund.getClawbackBonus())) {
            clawbackCommissions(finance, refund, cumulativeProductRefundRate);
        }
        recalculate(finance);
        financeDao.update(finance);
        notifyCustomerPolicyAfterRefund(refund, bonusBase, bonusRefund,
                bonusRefundQuantity, preciseBonusScope ? cumulativeBonusRefund : productRefunded.add(productRefund));
        return refund;
    }

    private void notifyCustomerPolicyAfterRefund(DmsFinanceRefund refund, BigDecimal bonusBase,
                                                 BigDecimal bonusRefund, Integer bonusRefundQuantity,
                                                 BigDecimal cumulativeBonusRefund) {
        Long ruleVersionId = relationSnapshotDao.selectByOrderId(refund.getOrderId()).stream()
                .map(DmsOrderRelationSnapshot::getRuleVersionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> commissionRecordDao.selectByOrderId(refund.getOrderId()).stream()
                        .map(DmsCommissionRecord::getRuleVersionId)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
        if (ruleVersionId == null) return;
        Long tenantId = TenantContext.getTenantId();
        DmsCommissionRuleVersion version = ruleVersionDao.selectById(tenantId, ruleVersionId);
        if (version == null) {
            Asserts.fail("订单原客户奖金程序版本不存在，已阻止不完整退款处理");
        }
        CustomerBonusPolicy policy = bonusPolicyRegistry.require(version.getVersionNo());
        policy.afterRefund(new CustomerBonusRefundContext(
                tenantId, ruleVersionId, refund.getOrderId(), refund.getId(),
                refund.getProductRefundAmount(), refund.getRefundQuantity(),
                bonusBase, bonusRefund, bonusRefundQuantity,
                cumulativeBonusRefund, refund.getRefundTime()));
    }

    @Override
    public List<DmsFinanceRefund> getRefundsByOrderId(Long orderId) {
        return refundDao.selectByOrderId(orderId);
    }

    @Override
    public List<CompanyShareSummaryVO> getCompanyShareSummary(String range, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(range, startDate, endDate);
        List<CompanyShareSummaryVO> summaries = companyShareDao.selectSummary(dateRange.startTime(), dateRange.endTime());
        for (CompanyShareSummaryVO summary : summaries) {
            if (summary.getOrderCount() == null) {
                summary.setOrderCount(0L);
            }
            summary.setShareAmount(nullToZero(summary.getShareAmount()));
        }
        return summaries;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DmsFinanceRiskRule> listRiskRules() {
        ensureDefaultRiskRules();
        return riskRuleDao.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsFinanceRiskRule saveRiskRule(DmsFinanceRiskRule rule) {
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()) {
            Asserts.fail("规则编码不能为空");
        }
        if (rule.getRuleName() == null || rule.getRuleName().isBlank()) {
            rule.setRuleName(rule.getRuleCode());
        }
        if (rule.getThresholdValue() == null) {
            rule.setThresholdValue(BigDecimal.ZERO);
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        DmsFinanceRiskRule oldRule = riskRuleDao.selectByCode(rule.getRuleCode());
        if (oldRule == null) {
            riskRuleDao.insert(rule);
        } else {
            riskRuleDao.update(rule);
        }
        return riskRuleDao.selectByCode(rule.getRuleCode());
    }

    @Override
    public List<FinanceRiskAlertVO> getRiskAlerts(String range, LocalDate startDate, LocalDate endDate) {
        FinanceSummaryVO summary = getFinanceSummary(range, startDate, endDate);
        List<FinanceRiskAlertVO> alerts = new ArrayList<>();
        for (DmsFinanceRiskRule rule : listRiskRules()) {
            if (!Integer.valueOf(1).equals(rule.getEnabled())) {
                continue;
            }
            switch (rule.getRuleCode()) {
                case "BONUS_PAYOUT_RATE_MAX" -> {
                    if (summary.getPayoutRate().compareTo(rule.getThresholdValue()) > 0) {
                        alerts.add(toAlert(rule, summary.getPayoutRate(), "奖金拨出率超过阈值"));
                    }
                }
                case "PROFIT_RATE_MIN" -> {
                    if (summary.getProfitRate().compareTo(rule.getThresholdValue()) < 0) {
                        alerts.add(toAlert(rule, summary.getProfitRate(), "利润率低于阈值"));
                    }
                }
                case "LOSS_ORDER_COUNT_MAX" -> {
                    BigDecimal current = BigDecimal.valueOf(summary.getRiskOrderCount());
                    if (current.compareTo(rule.getThresholdValue()) > 0) {
                        alerts.add(toAlert(rule, current, "亏损风险订单数超过阈值"));
                    }
                }
                default -> {
                }
            }
        }
        return alerts;
    }

    private DmsOrderFinance ensureFinance(Long orderId, String orderNo, BigDecimal payAmount) {
        DmsOrderFinance finance = financeDao.selectByOrderId(orderId);
        if (finance != null) {
            return finance;
        }
        finance = new DmsOrderFinance();
        finance.setOrderId(orderId);
        finance.setOrderNo(orderNo);
        finance.setPayAmount(nullToZero(payAmount));
        finance.setRefundAmount(BigDecimal.ZERO);
        finance.setNetPayAmount(nullToZero(payAmount));
        finance.setProductCost(BigDecimal.ZERO);
        finance.setBonusAmount(BigDecimal.ZERO);
        finance.setCompanyShareAmount(BigDecimal.ZERO);
        finance.setCompanyProfit(nullToZero(payAmount));
        finance.setRiskStatus(0);
        // 历史导入的业绩明细可能没有商城订单主表；只做临时展示，
        // 不创建无法归属租户的孤儿财务记录。
        if (shopOrderDao.selectById(orderId) == null) {
            return finance;
        }
        if (financeDao.insert(finance) != 1) {
            Asserts.fail("订单不属于当前租户");
        }
        return finance;
    }

    private void recalculate(DmsOrderFinance finance) {
        BigDecimal refund = refundDao.sumByOrderId(finance.getOrderId());
        BigDecimal bonus = commissionRecordDao.selectByOrderId(finance.getOrderId()).stream()
                .map(this::netCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal companyShare = companyShareDao.selectByOrderId(finance.getOrderId()).stream()
                .map(DmsOrderCompanyShare::getShareAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPay = nullToZero(finance.getPayAmount()).subtract(nullToZero(refund));
        BigDecimal profit = netPay
                .subtract(nullToZero(finance.getProductCost()))
                .subtract(bonus)
                .subtract(companyShare);

        finance.setRefundAmount(nullToZero(refund));
        finance.setNetPayAmount(netPay);
        finance.setBonusAmount(bonus);
        finance.setCompanyShareAmount(companyShare);
        finance.setCompanyProfit(profit);

        List<String> riskReasons = new ArrayList<>();
        if (profit.compareTo(BigDecimal.ZERO) < 0) riskReasons.add("订单利润为负");
        DmsShopOrder order = shopOrderDao.selectById(finance.getOrderId());
        BigDecimal merchandiseRevenue = order == null ? netPay : nullToZero(order.getTotalAmount())
                .subtract(nullToZero(order.getDiscountAmount()))
                .subtract(nullToZero(refundDao.sumProductByOrderId(finance.getOrderId())))
                .max(BigDecimal.ZERO);
        DmsFinanceRiskRule payoutRule = riskRuleDao.selectByCode("BONUS_PAYOUT_RATE_MAX");
        if (isEnabled(payoutRule) && merchandiseRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payoutRate = bonus.divide(merchandiseRevenue, 8, RoundingMode.HALF_UP);
            if (payoutRate.compareTo(payoutRule.getThresholdValue()) > 0) {
                riskReasons.add("奖金拨出率" + payoutRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                        + "%超过阈值" + payoutRule.getThresholdValue().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
            }
        }
        DmsFinanceRiskRule profitRule = riskRuleDao.selectByCode("PROFIT_RATE_MIN");
        if (isEnabled(profitRule) && netPay.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = profit.divide(netPay, 8, RoundingMode.HALF_UP);
            if (profitRate.compareTo(profitRule.getThresholdValue()) < 0) {
                riskReasons.add("利润率低于阈值");
            }
        }
        finance.setRiskStatus(riskReasons.isEmpty() ? 0 : 1);
        String baseRemark = finance.getRemark() == null ? "" : finance.getRemark().split("；风控：", 2)[0];
        finance.setRemark(riskReasons.isEmpty() ? baseRemark
                : baseRemark + (baseRemark.isBlank() ? "" : "；") + "风控：" + String.join("；", riskReasons));
    }

    private boolean isEnabled(DmsFinanceRiskRule rule) {
        return rule != null && Integer.valueOf(1).equals(rule.getEnabled()) && rule.getThresholdValue() != null;
    }

    private BigDecimal netCommissionAmount(DmsCommissionRecord record) {
        CommissionStatusEnum status = CommissionStatusEnum.getByValue(record.getStatus());
        if (status == CommissionStatusEnum.CANCELLED || status == CommissionStatusEnum.REFUNDED) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = nullToZero(record.getCommissionAmount());
        if (status == CommissionStatusEnum.SETTLED) {
            return amount.subtract(nullToZero(clawbackDao.sumByCommissionRecordId(record.getId())))
                    .max(BigDecimal.ZERO);
        }
        // 待结算记录在退款时已经直接减记 commission_amount，不能再次扣除追回流水。
        return amount;
    }

    private void clawbackCommissions(DmsOrderFinance finance, DmsFinanceRefund refund,
                                     BigDecimal cumulativeProductRefundRate) {
        if (cumulativeProductRefundRate == null || cumulativeProductRefundRate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<DmsCommissionRecord> records = commissionRecordDao.selectByOrderId(finance.getOrderId());
        for (DmsCommissionRecord record : records) {
            CommissionStatusEnum status = CommissionStatusEnum.getByValue(record.getStatus());
            if (status == CommissionStatusEnum.CANCELLED || status == CommissionStatusEnum.REFUNDED) {
                continue;
            }
            BigDecimal alreadyClawback = nullToZero(clawbackDao.sumByCommissionRecordId(record.getId()));
            BigDecimal originalCommission = record.getCommissionAmount();
            if (status == CommissionStatusEnum.PENDING) {
                originalCommission = originalCommission.add(alreadyClawback);
            }
            BigDecimal targetClawback = originalCommission.multiply(cumulativeProductRefundRate)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal remainingClawback = originalCommission.subtract(alreadyClawback);
            // 累计比例算出“截至本次应追回总额”，本次只追差额，避免多次部分退款重复追回。
            BigDecimal clawbackAmount = targetClawback.subtract(alreadyClawback)
                    .max(BigDecimal.ZERO).min(remainingClawback);
            if (clawbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (status == CommissionStatusEnum.PENDING) {
                clawbackPendingCommission(record, refund, clawbackAmount);
            } else if (status == CommissionStatusEnum.SETTLED) {
                clawbackSettledCommission(record, refund, clawbackAmount);
            }
        }
    }

    private void clawbackPendingCommission(DmsCommissionRecord record, DmsFinanceRefund refund, BigDecimal clawbackAmount) {
        BigDecimal newAmount = record.getCommissionAmount().subtract(clawbackAmount);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            newAmount = BigDecimal.ZERO;
        }
        Integer newStatus = newAmount.compareTo(BigDecimal.ZERO) == 0
                ? CommissionStatusEnum.REFUNDED.getValue()
                : CommissionStatusEnum.PENDING.getValue();
        commissionRecordDao.updateAmountAndStatus(
                record.getId(),
                newAmount,
                newStatus,
                "退款冲账减少待结算佣金: refundId=" + refund.getId()
        );
        if (accountDao.subtractUnsettledCommission(record.getAgentId(), clawbackAmount) != 1
                || accountDao.subtractTotalCommission(record.getAgentId(), clawbackAmount) != 1) {
            Asserts.fail("待结算佣金余额已变化，退款冲账已回滚，请重试");
        }
        insertClawback(record, refund, clawbackAmount, clawbackAmount, BigDecimal.ZERO, 1, 1);
    }

    private void clawbackSettledCommission(DmsCommissionRecord record, DmsFinanceRefund refund, BigDecimal clawbackAmount) {
        // A settled commission may have been distributed into one or more member
        // asset wallets (not only the legacy cash balance). Reverse the same
        // settlement flows first so points / bonus wallets cannot remain usable
        // after the related order is refunded.
        BigDecimal walletDeducted = clawbackSettledWallets(record, refund, clawbackAmount);
        DmsAgentAccount account = accountDao.selectByAgentIdForUpdate(record.getAgentId());
        if (account == null) Asserts.fail("代理资金账户不存在，退款冲账已停止");
        BigDecimal available = account == null ? BigDecimal.ZERO : nullToZero(account.getAvailableBalance());
        BigDecimal remaining = clawbackAmount.subtract(walletDeducted);
        BigDecimal cashDeducted = available.min(remaining);
        BigDecimal deducted = walletDeducted.add(cashDeducted);
        BigDecimal debt = clawbackAmount.subtract(deducted);
        if (cashDeducted.compareTo(BigDecimal.ZERO) > 0) {
            if (accountDao.subtractAvailableBalance(record.getAgentId(), cashDeducted) != 1) {
                Asserts.fail("可提现佣金余额已变化，退款冲账已回滚，请重试");
            }
        }
        if (accountDao.subtractSettledCommission(record.getAgentId(), clawbackAmount) != 1
                || accountDao.subtractTotalCommission(record.getAgentId(), clawbackAmount) != 1) {
            Asserts.fail("已结算佣金余额不足，退款冲账已回滚，请人工核对");
        }
        insertClawback(record, refund, clawbackAmount, deducted, debt, debt.compareTo(BigDecimal.ZERO) > 0 ? 3 : 2,
                debt.compareTo(BigDecimal.ZERO) > 0 ? 2 : 1);
    }

    private BigDecimal clawbackSettledWallets(DmsCommissionRecord record, DmsFinanceRefund refund,
                                               BigDecimal clawbackAmount) {
        List<DmsMemberAssetFlow> flows = memberAssetFlowDao.selectCommissionSettlementFlows(record.getAgentId(), record.getId());
        if (flows.isEmpty() || record.getCommissionAmount() == null || record.getCommissionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        DmsMemberAssetAccount balance = memberAssetAccountDao.selectByAgentIdAndAssetCode(
                record.getAgentId(), BalanceAsset.CODE);
        BigDecimal deductible = balance == null ? BigDecimal.ZERO
                : nullToZero(balance.getBalance()).min(clawbackAmount);
        if (deductible.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        AssetChangeDTO dto = new AssetChangeDTO();
        dto.setAgentId(record.getAgentId());
        dto.setAmount(deductible);
        dto.setBizType("COMMISSION_CLAWBACK");
        dto.setBizId(String.valueOf(refund.getId()));
        dto.setRequestId("COMMISSION_CLAWBACK-" + refund.getId() + "-" + record.getId());
        dto.setRemark("退款追回已结算佣金：" + record.getRecordNo() + "，refundId=" + refund.getId());
        memberAssetService.deduct(dto);
        return deductible;
    }

    private void insertClawback(DmsCommissionRecord record, DmsFinanceRefund refund, BigDecimal clawbackAmount,
                                BigDecimal deducted, BigDecimal debt, Integer type, Integer status) {
        DmsCommissionClawback clawback = new DmsCommissionClawback();
        clawback.setRefundId(refund.getId());
        clawback.setCommissionRecordId(record.getId());
        clawback.setOrderId(record.getOrderId());
        clawback.setOrderNo(record.getOrderNo());
        clawback.setAgentId(record.getAgentId());
        clawback.setAgentUserId(record.getAgentUserId());
        clawback.setAgentName(record.getAgentName());
        clawback.setOriginalCommissionAmount(record.getCommissionAmount());
        clawback.setClawbackAmount(clawbackAmount);
        clawback.setDeductedAmount(deducted);
        clawback.setDebtAmount(debt);
        clawback.setClawbackType(type);
        clawback.setStatus(status);
        clawback.setReason(refund.getReason());
        clawbackDao.insert(clawback);
    }

    private List<OrderAuditVO> buildOrderAuditList(List<DmsOrderPerformanceDetail> details) {
        Map<Long, DmsOrderPerformanceDetail> unique = new LinkedHashMap<>();
        for (DmsOrderPerformanceDetail detail : details) {
            unique.putIfAbsent(detail.getOrderId(), detail);
        }
        List<OrderAuditVO> result = new ArrayList<>();
        for (DmsOrderPerformanceDetail detail : unique.values()) {
            DmsOrderFinance finance = ensureFinance(detail.getOrderId(), detail.getOrderNo(), detail.getOrderAmount());
            OrderAuditVO vo = new OrderAuditVO();
            vo.setOrderId(detail.getOrderId());
            vo.setOrderNo(detail.getOrderNo());
            vo.setOrderAmount(detail.getOrderAmount());
            vo.setOrderTime(detail.getOrderTime());
            vo.setOwnerUserId(detail.getOwnerUserId());
            DmsShopMember owner = shopMemberDao.selectByUserId(detail.getOwnerUserId());
            vo.setOwnerMemberAccount(MemberAccountUtils.display(owner));
            vo.setOwnerMemberName(resolveMemberName(owner, detail.getOwnerAgentName()));
            vo.setOwnerAgentId(detail.getOwnerAgentId());
            vo.setOwnerAgentName(detail.getOwnerAgentName());
            vo.setProductCost(finance.getProductCost());
            vo.setBonusAmount(finance.getBonusAmount());
            vo.setCompanyProfit(finance.getCompanyProfit());
            vo.setRiskStatus(finance.getRiskStatus());
            result.add(vo);
        }
        return preservePage(details, result);
    }

    private List<OrderAuditVO> buildShopOrderAuditList(List<DmsShopOrder> orders) {
        List<OrderAuditVO> result = new ArrayList<>();
        for (DmsShopOrder order : orders) {
            DmsOrderFinance finance = ensureFinance(order.getId(), order.getOrderNo(), order.getPayAmount());
            DmsShopMember owner = shopMemberDao.selectByUserId(order.getUserId());
            DmsAgent ownerAgent = order.getAgentId() == null
                    ? agentDao.selectByUserId(order.getUserId())
                    : agentDao.selectById(order.getAgentId());
            OrderAuditVO vo = new OrderAuditVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setOrderAmount(order.getPayAmount());
            vo.setOrderTime(order.getPayTime() == null ? order.getCreateTime() : order.getPayTime());
            vo.setOwnerUserId(order.getUserId());
            vo.setOwnerMemberAccount(MemberAccountUtils.display(owner));
            vo.setOwnerMemberName(resolveMemberName(owner, ownerAgent == null ? null : ownerAgent.getAgentName()));
            vo.setOwnerAgentId(ownerAgent == null ? order.getAgentId() : ownerAgent.getId());
            vo.setOwnerAgentName(ownerAgent == null ? null : ownerAgent.getAgentName());
            vo.setProductCost(finance.getProductCost());
            vo.setBonusAmount(finance.getBonusAmount());
            vo.setCompanyProfit(finance.getCompanyProfit());
            vo.setRiskStatus(finance.getRiskStatus());
            result.add(vo);
        }
        return preservePage(orders, result);
    }

    private List<CommissionRecordVO> mapCommissionRecords(List<DmsCommissionRecord> records) {
        return preservePage(records, records.stream().map(this::toCommissionVO).toList());
    }

    private <S, T> List<T> preservePage(List<S> source, List<T> converted) {
        if (!(source instanceof Page<?> sourcePage)) return converted;
        Page<T> result = new Page<>(sourcePage.getPageNum(), sourcePage.getPageSize());
        result.setTotal(sourcePage.getTotal());
        result.addAll(converted);
        return result;
    }

    private String resolveMemberName(DmsShopMember member, String fallback) {
        if (member == null) return fallback;
        if (member.getNickname() != null && !member.getNickname().isBlank()) return member.getNickname();
        if (member.getUsername() != null && !member.getUsername().isBlank()) return member.getUsername();
        if (member.getPhone() != null && !member.getPhone().isBlank()) return member.getPhone();
        return fallback;
    }

    private boolean getBooleanSetting(String key, boolean defaultValue) {
        DmsDistributionSetting setting = settingDao.selectByKey(key);
        return setting == null ? defaultValue : Boolean.parseBoolean(setting.getSettingValue());
    }

    private void saveSetting(String key, String value, String remark) {
        DmsDistributionSetting setting = settingDao.selectByKey(key);
        if (setting == null) {
            setting = new DmsDistributionSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setRemark(remark);
            settingDao.insert(setting);
        } else {
            setting.setSettingValue(value);
            setting.setRemark(remark);
            settingDao.updateByKey(setting);
        }
    }

    private PerformanceViewPermissionVO toPermissionVO(DmsPerformanceViewPermission permission) {
        PerformanceViewPermissionVO vo = new PerformanceViewPermissionVO();
        BeanUtils.copyProperties(permission, vo);
        DmsShopMember member = shopMemberDao.selectByUserId(permission.getUserId());
        vo.setMemberAccount(MemberAccountUtils.display(member));
        return vo;
    }

    private CommissionRecordVO toCommissionVO(DmsCommissionRecord record) {
        CommissionRecordVO vo = new CommissionRecordVO();
        BeanUtils.copyProperties(record, vo);
        DmsShopMember receiver = shopMemberDao.selectByUserId(record.getAgentUserId());
        DmsShopMember buyer = shopMemberDao.selectByUserId(record.getOrderUserId());
        vo.setAgentMemberAccount(MemberAccountUtils.display(receiver));
        vo.setOrderMemberAccount(MemberAccountUtils.display(buyer));
        vo.setCommissionLevelName(record.getCommissionLevel() == null ? "未知" : record.getCommissionLevel() + "级");
        CommissionStatusEnum status = CommissionStatusEnum.getByValue(record.getStatus());
        vo.setStatusName(status == null ? "未知" : status.getName());
        return vo;
    }

    private OrderFinanceVO toFinanceVO(DmsOrderFinance finance) {
        recalculate(finance);
        OrderFinanceVO vo = new OrderFinanceVO();
        BeanUtils.copyProperties(finance, vo);
        vo.setRiskStatusName(Integer.valueOf(1).equals(finance.getRiskStatus()) ? "亏损风险" : "正常");
        return vo;
    }

    private OrderCompanyShareVO toCompanyShareVO(DmsOrderCompanyShare share) {
        OrderCompanyShareVO vo = new OrderCompanyShareVO();
        BeanUtils.copyProperties(share, vo);
        return vo;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void fillSummaryDefaults(FinanceSummaryVO summary) {
        if (summary.getOrderCount() == null) {
            summary.setOrderCount(0L);
        }
        if (summary.getRiskOrderCount() == null) {
            summary.setRiskOrderCount(0L);
        }
        summary.setPayAmount(nullToZero(summary.getPayAmount()));
        summary.setRefundAmount(nullToZero(summary.getRefundAmount()));
        summary.setNetPayAmount(nullToZero(summary.getNetPayAmount()));
        summary.setProductCost(nullToZero(summary.getProductCost()));
        summary.setBonusAmount(nullToZero(summary.getBonusAmount()));
        summary.setCompanyShareAmount(nullToZero(summary.getCompanyShareAmount()));
        summary.setCompanyProfit(nullToZero(summary.getCompanyProfit()));
    }

    private DateRange resolveDateRange(String range, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        LocalDate today = LocalDate.now();
        String normalizedRange = range == null || range.isBlank() ? "today" : range;
        switch (normalizedRange) {
            case "today" -> {
                startTime = today.atStartOfDay();
                endTime = today.plusDays(1).atStartOfDay();
            }
            case "7days" -> {
                startTime = today.minusDays(6).atStartOfDay();
                endTime = today.plusDays(1).atStartOfDay();
            }
            case "month" -> {
                startTime = today.withDayOfMonth(1).atStartOfDay();
                endTime = today.plusDays(1).atStartOfDay();
            }
            case "custom" -> {
                if (startDate == null || endDate == null) {
                    Asserts.fail("自定义日期需要开始日期和结束日期");
                }
                if (endDate.isBefore(startDate)) {
                    Asserts.fail("结束日期不能早于开始日期");
                }
                startTime = startDate.atStartOfDay();
                endTime = endDate.plusDays(1).atStartOfDay();
            }
            case "total" -> {
            }
            default -> Asserts.fail("不支持的统计范围");
        }
        return new DateRange(startTime, endTime);
    }

    private record DateRange(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private void ensureDefaultRiskRules() {
        saveDefaultRiskRule("BONUS_PAYOUT_RATE_MAX", "奖金拨出率预警阈值", new BigDecimal("0.35"),
                "通用运营预警阈值；客户项目应根据已确认制度和利润模型单独校准");
        saveDefaultRiskRule("PROFIT_RATE_MIN", "利润率下限", new BigDecimal("0.10"), "利润率低于该值时预警");
        saveDefaultRiskRule("LOSS_ORDER_COUNT_MAX", "亏损订单数上限", BigDecimal.ZERO, "亏损风险订单数大于该值时预警");
    }

    private void saveDefaultRiskRule(String code, String name, BigDecimal threshold, String remark) {
        if (riskRuleDao.selectByCode(code) != null) {
            return;
        }
        DmsFinanceRiskRule rule = new DmsFinanceRiskRule();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setThresholdValue(threshold);
        rule.setEnabled(1);
        rule.setRemark(remark);
        riskRuleDao.insert(rule);
    }

    private FinanceRiskAlertVO toAlert(DmsFinanceRiskRule rule, BigDecimal currentValue, String message) {
        FinanceRiskAlertVO alert = new FinanceRiskAlertVO();
        alert.setRuleCode(rule.getRuleCode());
        alert.setRuleName(rule.getRuleName());
        alert.setMessage(message);
        alert.setCurrentValue(currentValue);
        alert.setThresholdValue(rule.getThresholdValue());
        alert.setLevel(2);
        return alert;
    }
}
