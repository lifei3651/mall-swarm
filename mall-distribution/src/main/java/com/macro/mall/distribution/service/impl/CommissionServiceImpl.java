package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.bonus.CustomerBonusOrderContext;
import com.macro.mall.distribution.bonus.CustomerBonusPayout;
import com.macro.mall.distribution.bonus.CustomerBonusPolicy;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyRegistry;
import com.macro.mall.distribution.bonus.CustomerBonusPayoutValidator;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.CommissionRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.macro.mall.distribution.service.impl.NewRetailBonusPolicy.DIRECTOR_SHARE;
import static com.macro.mall.distribution.service.impl.NewRetailBonusPolicy.DIRECT_REWARD;

/**
 * 佣金服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final DmsCommissionRecordDao recordDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final DmsOrderRelationSnapshotDao orderRelationSnapshotDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsAgentAccountDao accountDao;
    private final DmsCommissionClawbackDao clawbackDao;
    private final DmsShopOrderDao shopOrderDao;
    private final DmsShopAfterSaleDao shopAfterSaleDao;
    private final DmsShopMemberDao shopMemberDao;
    private final AgentAccountService accountService;
    private final DistributionAuditService auditService;
    private final MemberAssetService memberAssetService;
    private final PerformanceService performanceService;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    private final CustomerBonusPolicyRegistry bonusPolicyRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateAndRecordCommission(Long orderId, String orderNo, BigDecimal orderAmount,
                                              Long orderUserId, String orderUserName) {
        calculateAndRecordCommission(1L, orderId, orderNo, orderAmount, orderUserId, orderUserName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateAndRecordCommission(Long tenantId, Long orderId, String orderNo, BigDecimal orderAmount,
                                              Long orderUserId, String orderUserName) {
        Long resolvedTenantId = tenantId == null ? 1L : tenantId;
        DmsCommissionRuleVersion version = resolveOrderRuleVersion(resolvedTenantId, orderId);
        if (version == null) {
            Asserts.fail("当前客户奖金程序尚未接入，已阻止产生不确定奖金");
        }
        if (!recordDao.selectByOrderId(orderId).isEmpty()) {
            log.info("订单奖金已计算，忽略重复请求: orderId={}", orderId);
            return;
        }

        CustomerBonusPolicy policy = bonusPolicyRegistry.require(version.getVersionNo());
        CustomerBonusOrderContext context = new CustomerBonusOrderContext(
                resolvedTenantId, version.getId(), orderId, orderNo, orderAmount, orderUserId, orderUserName);
        List<CustomerBonusPayout> payouts = CustomerBonusPayoutValidator.validate(context, policy.calculate(context));
        for (CustomerBonusPayout payout : payouts) {
            addPendingRecord(context, payout);
        }
        policy.afterOrder(context);
        auditService.refreshOrderFinance(orderId, orderNo, orderAmount);
    }

    /**
     * 正常支付订单必须使用支付瞬间冻结在关系快照中的客户制度版本。
     * 仅为没有关系快照的历史导入或兼容调用保留“当前启用版本”回退。
     */
    private DmsCommissionRuleVersion resolveOrderRuleVersion(Long tenantId, Long orderId) {
        List<Long> frozenVersionIds = orderRelationSnapshotDao.selectByOrderId(orderId).stream()
                .map(DmsOrderRelationSnapshot::getRuleVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (frozenVersionIds.size() > 1) {
            Asserts.fail("订单关系快照包含多个客户奖金程序版本，已阻止不一致计算");
        }
        if (frozenVersionIds.size() == 1) {
            DmsCommissionRuleVersion frozenVersion = ruleVersionDao.selectById(tenantId, frozenVersionIds.get(0));
            if (frozenVersion == null) {
                Asserts.fail("订单冻结的客户奖金程序版本不存在，已阻止不完整计算");
            }
            return frozenVersion;
        }
        return ruleVersionDao.selectActiveByTenantId(tenantId);
    }

    private void addPendingRecord(CustomerBonusOrderContext context, CustomerBonusPayout payout) {
        DmsAgent receiver = agentDao.selectById(payout.receiverAgentId());
        if (receiver == null || receiver.getUserId() == null) {
            Asserts.fail("客户奖金程序指定的接收人不存在");
        }
        BigDecimal amount = payout.amount() == null ? BigDecimal.ZERO
                : payout.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = payout.rate() == null ? BigDecimal.ZERO
                : payout.rate().setScale(4, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (payout.bonusCode() == null || payout.bonusCode().isBlank()) {
            Asserts.fail("客户奖金程序返回了缺少类型编码的结果");
        }
        DmsCommissionRecord record = new DmsCommissionRecord();
        record.setTenantId(context.tenantId());
        record.setRuleVersionId(context.ruleVersionId());
        record.setRecordNo(generateRecordNo());
        record.setOrderId(context.orderId());
        record.setOrderNo(context.orderNo());
        record.setOrderAmount(context.bonusBaseAmount());
        record.setOrderUserId(context.orderUserId());
        record.setOrderUserName(context.orderUserName());
        record.setAgentId(receiver.getId());
        record.setAgentUserId(receiver.getUserId());
        record.setAgentName(receiver.getAgentName());
        record.setAgentLevel(receiver.getAgentLevel());
        record.setCommissionLevel(payout.relationshipLevel() == null ? 0 : payout.relationshipLevel());
        record.setBonusType(payout.bonusCode().trim());
        record.setCommissionRate(rate);
        record.setCommissionAmount(amount);
        record.setStatus(CommissionStatusEnum.PENDING.getValue());
        record.setRemark(payout.remark());
        recordDao.insert(record);
        DebtOffsetResult offset = offsetAgentDebt(record, amount);
        if (offset.offsetAmount().compareTo(BigDecimal.ZERO) > 0) {
            record.setCommissionAmount(offset.payableAmount());
            String baseRemark = payout.remark() == null ? "" : payout.remark();
            String suffix = "历史退款欠款自动抵扣：" + offset.offsetAmount();
            int baseLimit = Math.max(0, 256 - suffix.length() - (baseRemark.isBlank() ? 0 : 1));
            if (baseRemark.length() > baseLimit) baseRemark = baseRemark.substring(0, baseLimit);
            record.setRemark(baseRemark + (baseRemark.isBlank() ? "" : "；") + suffix);
            if (offset.payableAmount().compareTo(BigDecimal.ZERO) == 0) record.setStatus(CommissionStatusEnum.REFUNDED.getValue());
            recordDao.updateAmountAndStatus(record.getId(), record.getCommissionAmount(), record.getStatus(), record.getRemark());
        }
        accountService.addCommission(receiver.getId(), offset.payableAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleCommission(Long recordId) {
        return doSettleCommission(recordId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleCommissionIfEligible(Long recordId) {
        DmsCommissionRecord record = recordDao.selectByIdForUpdate(recordId);
        if (record == null || !CommissionStatusEnum.PENDING.getValue().equals(record.getStatus())) return false;
        DmsShopOrder order = shopOrderDao.selectByIdForUpdate(record.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        if (order == null || !Integer.valueOf(3).equals(order.getStatus()) || order.getReceiveTime() == null) return false;
        LocalDateTime afterSaleDeadline = afterSaleWindowPolicy.deadline(order);
        if (afterSaleDeadline != null && now.isBefore(afterSaleDeadline)) return false;
        if (shopAfterSaleDao.selectOpenByOrderId(order.getId()) != null) return false;
        return settleLockedRecord(record) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleCommissionBatch(List<Long> recordIds) {
        int count = 0;
        for (Long recordId : recordIds) {
            // 直接调用核心逻辑，不通过this调用（避免自调用绕过事务）
            count += doSettleCommission(recordId);
        }
        return count;
    }

    /**
     * 佣金结算核心逻辑（提取为私有方法，供settleCommission和settleCommissionBatch共用）
     */
    private int doSettleCommission(Long recordId) {
        DmsCommissionRecord record = recordDao.selectByIdForUpdate(recordId);
        if (record == null) {
            log.warn("佣金记录不存在: recordId={}", recordId);
            return 0;
        }
        if (!CommissionStatusEnum.PENDING.getValue().equals(record.getStatus())) {
            log.warn("佣金状态不正确，无法结算: recordId={}, status={}", recordId, record.getStatus());
            return 0;
        }

        return settleLockedRecord(record);
    }

    private int settleLockedRecord(DmsCommissionRecord record) {
        // 更新佣金记录状态
        record.setStatus(CommissionStatusEnum.SETTLED.getValue());
        record.setSettleTime(LocalDateTime.now());
        recordDao.update(record);

        // 结算佣金（从待结算转为已结算）
        accountService.settleCommission(record.getAgentId(), record.getCommissionAmount());
        // 通过钱包系统入账（issueCommissionToWallets已处理可提现余额，避免双重计数）
        issueCommissionToWallets(record);

        log.info("结算佣金成功: recordId={}, agentId={}, amount={}", record.getId(), record.getAgentId(), record.getCommissionAmount());
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelCommission(Long recordId, String cancelReason) {
        String normalizedReason = cancelReason == null ? "" : cancelReason.trim();
        if (normalizedReason.isEmpty()) {
            Asserts.fail("请输入取消原因");
        }
        if (normalizedReason.length() > 200) {
            Asserts.fail("取消原因不能超过200个字符");
        }
        DmsCommissionRecord record = recordDao.selectByIdForUpdate(recordId);
        if (record == null) {
            Asserts.fail("佣金记录不存在");
        }
        if (!CommissionStatusEnum.PENDING.getValue().equals(record.getStatus())) {
            Asserts.fail("佣金状态不正确，无法取消");
        }

        // 更新佣金记录状态
        record.setStatus(CommissionStatusEnum.CANCELLED.getValue());
        record.setCancelReason(normalizedReason);
        recordDao.update(record);

        // 减少代理账户的待结算佣金
        accountService.subtractUnsettledCommission(record.getAgentId(), record.getCommissionAmount());

        log.info("取消佣金成功: recordId={}, agentId={}, amount={}",
                recordId, record.getAgentId(), record.getCommissionAmount());
        return true;
    }

    @Override
    public List<CommissionRecordVO> getCommissionRecords(CommissionQueryDTO queryDTO) {
        if (queryDTO.getMemberKey() != null && !queryDTO.getMemberKey().isBlank()) {
            queryDTO.setAgentId(performanceService.resolveAgentId(queryDTO.getMemberKey()));
        }
        if (queryDTO.getPageNum() != null && queryDTO.getPageSize() != null) {
            PageHelper.startPage(queryDTO.getPageNum(), queryDTO.getPageSize());
        }

        return convertToVOList(recordDao.selectByQuery(queryDTO));
    }

    @Override
    public BigDecimal getUnsettledAmount(Long agentId) {
        return recordDao.selectUnsettledAmountByAgentId(agentId);
    }

    @Override
    public BigDecimal getSettledAmount(Long agentId) {
        return recordDao.selectSettledAmountByAgentId(agentId);
    }

    /**
     * 生成记录编号
     */
    private String generateRecordNo() {
        return "COM" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * 新佣金优先抵扣历史退款欠款，剩余净额才进入待结算。
     */
    private DebtOffsetResult offsetAgentDebt(DmsCommissionRecord record, BigDecimal commissionAmount) {
        BigDecimal remainingCommission = commissionAmount;
        BigDecimal totalOffset = BigDecimal.ZERO;
        List<DmsCommissionClawback> debtRows = clawbackDao.selectPendingDebtByAgentId(record.getAgentId());
        for (DmsCommissionClawback debtRow : debtRows) {
            if (remainingCommission.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal debtAmount = nullToZero(debtRow.getDebtAmount());
            if (debtAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal offsetAmount = remainingCommission.min(debtAmount);
            BigDecimal newDeductedAmount = nullToZero(debtRow.getDeductedAmount()).add(offsetAmount);
            BigDecimal newDebtAmount = debtAmount.subtract(offsetAmount);
            Integer status = newDebtAmount.compareTo(BigDecimal.ZERO) == 0 ? 1 : 2;
            clawbackDao.updateDebtAfterOffset(debtRow.getId(), newDeductedAmount, newDebtAmount, status);

            DmsCommissionClawback offsetFlow = new DmsCommissionClawback();
            offsetFlow.setRefundId(0L);
            offsetFlow.setCommissionRecordId(record.getId());
            offsetFlow.setOrderId(record.getOrderId());
            offsetFlow.setOrderNo(record.getOrderNo());
            offsetFlow.setAgentId(record.getAgentId());
            offsetFlow.setAgentUserId(record.getAgentUserId());
            offsetFlow.setAgentName(record.getAgentName());
            offsetFlow.setOriginalCommissionAmount(commissionAmount);
            offsetFlow.setClawbackAmount(offsetAmount);
            offsetFlow.setDeductedAmount(offsetAmount);
            offsetFlow.setDebtAmount(BigDecimal.ZERO);
            offsetFlow.setClawbackType(4);
            offsetFlow.setStatus(1);
            offsetFlow.setReason("历史退款欠款自动抵扣，来源追回流水ID：" + debtRow.getId());
            clawbackDao.insert(offsetFlow);

            remainingCommission = remainingCommission.subtract(offsetAmount);
            totalOffset = totalOffset.add(offsetAmount);
        }
        return new DebtOffsetResult(totalOffset, remainingCommission);
    }

    private BigDecimal nullToZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private record DebtOffsetResult(BigDecimal offsetAmount, BigDecimal payableAmount) {
    }

    private void issueCommissionToWallets(DmsCommissionRecord record) {
        if (record.getCommissionAmount() == null || record.getCommissionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        issueToBalance(record, record.getCommissionAmount());
    }

    private void issueToBalance(DmsCommissionRecord record, BigDecimal amount) {
        AssetChangeDTO dto = new AssetChangeDTO();
        dto.setAgentId(record.getAgentId());
        dto.setAmount(amount);
        dto.setBizType("COMMISSION_SETTLE");
        dto.setBizId(String.valueOf(record.getId()));
        dto.setRequestId("COMMISSION_SETTLE-" + record.getId());
        dto.setRemark("佣金结算进入余额：" + record.getRecordNo());
        memberAssetService.issue(dto);
    }

    /**
     * 转换为VO列表
     */
    private List<CommissionRecordVO> convertToVOList(List<DmsCommissionRecord> records) {
        List<CommissionRecordVO> voList = new ArrayList<>();
        for (DmsCommissionRecord record : records) {
            CommissionRecordVO vo = new CommissionRecordVO();
            BeanUtils.copyProperties(record, vo);
            DmsShopMember receiver = shopMemberDao.selectByUserId(record.getAgentUserId());
            DmsShopMember buyer = shopMemberDao.selectByUserId(record.getOrderUserId());
            vo.setAgentMemberAccount(MemberAccountUtils.display(receiver));
            vo.setOrderMemberAccount(MemberAccountUtils.display(buyer));

            // 设置佣金层级名称
            vo.setCommissionLevelName(getCommissionLevelName(record));

            // 设置状态名称
            CommissionStatusEnum statusEnum = CommissionStatusEnum.getByValue(record.getStatus());
            vo.setStatusName(statusEnum != null ? statusEnum.getName() : "未知");

            voList.add(vo);
        }
        return voList;
    }

    /**
     * 获取佣金层级名称
     */
    private String getCommissionLevelName(DmsCommissionRecord record) {
        if (DIRECT_REWARD.equals(record.getBonusType())) {
            return "直推奖";
        }
        if (DIRECTOR_SHARE.equals(record.getBonusType())) {
            return "无限层团队分红（第" + record.getCommissionLevel() + "层）";
        }
        if (record.getRemark() != null && !record.getRemark().isBlank()) {
            return record.getRemark().split("；", 2)[0];
        }
        return record.getBonusType() == null || record.getBonusType().isBlank()
                ? "客户奖金" : record.getBonusType();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleAgentAndDescendantCommissions(Long agentId) {
        int totalSettled = 0;

        // 1. 结算该代理的待结算佣金
        List<DmsCommissionRecord> unsettledRecords = recordDao.selectByAgentIdAndStatus(
                agentId, CommissionStatusEnum.PENDING.getValue());
        for (DmsCommissionRecord record : unsettledRecords) {
            // 批量结算必须与钱包入账共用同一个事务。任何一笔失败都向上抛出，
            // 避免出现“佣金已结算、但钱包未入账”的半成功状态。
            if (doSettleCommission(record.getId()) > 0) {
                totalSettled++;
            }
        }

        // 2. 递归结算所有下级的待结算佣金
        totalSettled += settleDescendantCommissions(agentId);

        log.info("结算代理及其下级佣金完成: agentId={}, totalSettled={}", agentId, totalSettled);
        return totalSettled;
    }

    /**
     * 递归结算下级代理的待结算佣金
     */
    private int settleDescendantCommissions(Long agentId) {
        int totalSettled = 0;

        // 查询该代理的所有直属下级
        List<DmsAgentRelation> children = relationDao.selectDirectChildren(agentId);

        for (DmsAgentRelation child : children) {
            DmsAgent childAgent = agentDao.selectById(child.getAgentId());
            if (childAgent == null) {
                continue;
            }

            // 结算该下级的待结算佣金
            List<DmsCommissionRecord> unsettledRecords = recordDao.selectByAgentIdAndStatus(
                    childAgent.getId(), CommissionStatusEnum.PENDING.getValue());
            for (DmsCommissionRecord record : unsettledRecords) {
                if (doSettleCommission(record.getId()) > 0) {
                    totalSettled++;
                }
            }

            // 递归结算下级的下级
            totalSettled += settleDescendantCommissions(childAgent.getId());
        }

        return totalSettled;
    }
}
