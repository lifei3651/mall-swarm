package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsBonusCalculationSnapshotDao;
import com.macro.mall.distribution.dao.DmsBonusCalculationTaskDao;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsMemberAssetFlowDao;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsBonusCalculationSnapshot;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import com.macro.mall.distribution.entity.DmsCommissionClawback;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.CommissionRecordVO;
import com.macro.mall.distribution.vo.OrderBonusTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 汇总已有真实证据，不参与奖金计算和资金写入。 */
@Service
@RequiredArgsConstructor
public class OrderBonusTraceService {

    private final DmsBonusCalculationTaskDao taskDao;
    private final DmsBonusCalculationSnapshotDao calculationSnapshotDao;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final DmsShopMemberDao memberDao;
    private final DmsMemberAssetFlowDao assetFlowDao;

    public OrderBonusTraceVO build(DmsShopOrder order,
                                   List<DmsCommissionRecord> records,
                                   List<CommissionRecordVO> actualRecords,
                                   List<DmsFinanceRefund> refunds,
                                   List<DmsCommissionClawback> clawbacks) {
        OrderBonusTraceVO trace = new OrderBonusTraceVO();
        trace.setOrderId(order.getId());
        trace.setOrderNo(order.getOrderNo());

        List<DmsCommissionRecord> safeRecords = records == null ? List.of() : records;
        List<CommissionRecordVO> safeActualRecords = actualRecords == null ? List.of() : actualRecords;
        List<DmsFinanceRefund> safeRefunds = refunds == null ? List.of() : refunds;
        List<DmsCommissionClawback> safeClawbacks = clawbacks == null ? List.of() : clawbacks;
        List<DmsOrderRelationSnapshot> relations = relationSnapshotDao.selectByOrderId(order.getId());
        List<DmsBonusCalculationSnapshot> evidence = calculationSnapshotDao.selectByOrderId(order.getId());
        DmsBonusCalculationTask task = taskDao.selectLatestByOrderId(order.getId());

        trace.setActualRecords(new ArrayList<>(safeActualRecords));
        trace.setRelationChain(mapRelations(relations));
        trace.setRelationCount(relations.size());
        trace.setCalculationEvidence(mapEvidence(evidence));
        fillTask(trace, task);
        fillRuleVersion(trace, relations, safeRecords, task);

        Map<Long, DmsCommissionRecord> recordsById = new LinkedHashMap<>();
        safeRecords.forEach(record -> recordsById.put(record.getId(), record));
        Map<Long, BigDecimal> clawbackByRecord = new LinkedHashMap<>();
        Map<Long, BigDecimal> originalByRecord = new LinkedHashMap<>();
        for (DmsCommissionRecord record : safeRecords) {
            originalByRecord.put(record.getId(), amount(record.getCommissionAmount()));
        }
        for (DmsCommissionClawback clawback : safeClawbacks) {
            // 待结算退款和历史欠款抵扣都会直接减记 commission_amount；只有已结算后的
            // 退款追回才需要在展示净额时从记录金额再次扣除，避免结算后重复减记。
            if (Integer.valueOf(2).equals(clawback.getClawbackType())
                    || Integer.valueOf(3).equals(clawback.getClawbackType())) {
                clawbackByRecord.merge(clawback.getCommissionRecordId(), amount(clawback.getClawbackAmount()), BigDecimal::add);
            }
            originalByRecord.merge(clawback.getCommissionRecordId(), amount(clawback.getOriginalCommissionAmount()), BigDecimal::max);
        }

        List<OrderBonusTraceVO.AssetFlow> assetFlows = collectAssetFlows(safeRecords, safeClawbacks, recordsById);
        trace.setAssetFlows(assetFlows);
        trace.setClawbacks(mapClawbacks(safeClawbacks, recordsById));
        fillAmounts(trace, safeRecords, evidence, clawbackByRecord, originalByRecord, safeClawbacks, assetFlows);
        fillStatus(trace, order, safeRecords, relations, task, safeClawbacks);
        trace.setTimeline(buildTimeline(order, relations, evidence, task, safeRecords, safeRefunds, safeClawbacks));
        return trace;
    }

    private List<OrderBonusTraceVO.RelationNode> mapRelations(List<DmsOrderRelationSnapshot> relations) {
        List<OrderBonusTraceVO.RelationNode> result = new ArrayList<>();
        for (DmsOrderRelationSnapshot relation : relations) {
            DmsShopMember member = memberDao.selectByUserId(relation.getTargetUserId());
            OrderBonusTraceVO.RelationNode node = new OrderBonusTraceVO.RelationNode();
            node.setRelationLevel(relation.getRelationLevel());
            node.setMemberAccount(MemberAccountUtils.display(member));
            node.setMemberName(memberName(member, relation.getTargetAgentName()));
            node.setRelationPath(relation.getRelationPath());
            node.setSnapshotTime(relation.getSnapshotTime());
            result.add(node);
        }
        return result;
    }

    private List<OrderBonusTraceVO.CalculationEvidence> mapEvidence(List<DmsBonusCalculationSnapshot> snapshots) {
        List<OrderBonusTraceVO.CalculationEvidence> result = new ArrayList<>();
        for (DmsBonusCalculationSnapshot snapshot : snapshots) {
            OrderBonusTraceVO.CalculationEvidence item = new OrderBonusTraceVO.CalculationEvidence();
            item.setId(snapshot.getId());
            item.setTotalPv(amount(snapshot.getTotalPv()));
            item.setTotalBonus(amount(snapshot.getTotalBonus()));
            item.setRiskStatus(snapshot.getRiskStatus());
            item.setRiskStatusName("BLOCK".equals(snapshot.getRiskStatus()) ? "已拦截" : "PASS".equals(snapshot.getRiskStatus()) ? "通过" : "未标记");
            item.setCreateTime(snapshot.getCreateTime());
            result.add(item);
        }
        return result;
    }

    private void fillTask(OrderBonusTraceVO trace, DmsBonusCalculationTask task) {
        if (task == null) {
            trace.setCalculationTaskStatusName("支付流程同步计算");
            return;
        }
        trace.setCalculationTaskId(task.getId());
        trace.setCalculationTaskStatus(task.getStatus());
        trace.setCalculationTaskStatusName(switch (task.getStatus() == null ? -1 : task.getStatus()) {
            case 0 -> "待计算";
            case 1 -> "计算中";
            case 2 -> "计算成功";
            case 3 -> "计算失败";
            default -> "未知";
        });
        trace.setCalculationRetryCount(task.getRetryCount());
        trace.setCalculationFailReason(task.getFailReason());
    }

    private void fillRuleVersion(OrderBonusTraceVO trace,
                                 List<DmsOrderRelationSnapshot> relations,
                                 List<DmsCommissionRecord> records,
                                 DmsBonusCalculationTask task) {
        Set<Long> versionIds = new LinkedHashSet<>();
        relations.stream().map(DmsOrderRelationSnapshot::getRuleVersionId).filter(Objects::nonNull).forEach(versionIds::add);
        records.stream().map(DmsCommissionRecord::getRuleVersionId).filter(Objects::nonNull).forEach(versionIds::add);
        if (task != null && task.getRuleVersionId() != null) versionIds.add(task.getRuleVersionId());
        if (versionIds.isEmpty()) return;
        Long versionId = versionIds.iterator().next();
        trace.setRuleVersionId(versionId);
        DmsCommissionRuleVersion version = ruleVersionDao.selectById(TenantContext.getTenantId(), versionId);
        if (version != null) {
            trace.setRuleVersionNo(version.getVersionNo());
            trace.setRuleVersionName(version.getVersionName());
        }
        if (versionIds.size() > 1) {
            trace.setStatus("DATA_CONFLICT");
            trace.setStatusName("数据需核对");
            trace.setExplanation("同一订单出现多个奖金程序版本，已在追溯页明确标记，请暂停人工结算并核对。");
        }
    }

    private List<OrderBonusTraceVO.AssetFlow> collectAssetFlows(List<DmsCommissionRecord> records,
                                                                 List<DmsCommissionClawback> clawbacks,
                                                                 Map<Long, DmsCommissionRecord> recordsById) {
        Map<Long, OrderBonusTraceVO.AssetFlow> unique = new LinkedHashMap<>();
        for (DmsCommissionRecord record : records) {
            for (DmsMemberAssetFlow flow : assetFlowDao.selectCommissionSettlementFlows(record.getAgentId(), record.getId())) {
                unique.putIfAbsent(flow.getId(), toAssetFlow(flow, record, "SETTLEMENT", "奖金入账"));
            }
        }
        for (DmsCommissionClawback clawback : clawbacks) {
            if (clawback.getRefundId() == null || clawback.getRefundId() <= 0) continue;
            DmsCommissionRecord record = recordsById.get(clawback.getCommissionRecordId());
            if (record == null) continue;
            for (DmsMemberAssetFlow flow : assetFlowDao.selectCommissionClawbackFlows(
                    record.getAgentId(), clawback.getRefundId(), record.getRecordNo())) {
                unique.putIfAbsent(flow.getId(), toAssetFlow(flow, record, "CLAWBACK", "退款扣回"));
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(OrderBonusTraceVO.AssetFlow::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private OrderBonusTraceVO.AssetFlow toAssetFlow(DmsMemberAssetFlow flow,
                                                     DmsCommissionRecord record,
                                                     String action,
                                                     String actionName) {
        DmsShopMember member = memberDao.selectByUserId(record.getAgentUserId());
        OrderBonusTraceVO.AssetFlow item = new OrderBonusTraceVO.AssetFlow();
        item.setId(flow.getId());
        item.setCommissionRecordId(record.getId());
        item.setRecordNo(record.getRecordNo());
        item.setMemberAccount(MemberAccountUtils.display(member));
        item.setMemberName(memberName(member, record.getAgentName()));
        item.setFlowNo(flow.getFlowNo());
        item.setAction(action);
        item.setActionName(actionName);
        item.setAmount(amount(flow.getAmount()));
        item.setBalanceBefore(amount(flow.getBalanceBefore()));
        item.setBalanceAfter(amount(flow.getBalanceAfter()));
        item.setRemark(flow.getRemark());
        item.setCreateTime(flow.getCreateTime());
        return item;
    }

    private List<OrderBonusTraceVO.Clawback> mapClawbacks(List<DmsCommissionClawback> clawbacks,
                                                          Map<Long, DmsCommissionRecord> recordsById) {
        List<OrderBonusTraceVO.Clawback> result = new ArrayList<>();
        for (DmsCommissionClawback row : clawbacks) {
            DmsCommissionRecord record = recordsById.get(row.getCommissionRecordId());
            DmsShopMember member = record == null ? memberDao.selectByUserId(row.getAgentUserId())
                    : memberDao.selectByUserId(record.getAgentUserId());
            OrderBonusTraceVO.Clawback item = new OrderBonusTraceVO.Clawback();
            item.setId(row.getId());
            item.setRefundId(row.getRefundId());
            item.setCommissionRecordId(row.getCommissionRecordId());
            item.setRecordNo(record == null ? null : record.getRecordNo());
            item.setMemberAccount(MemberAccountUtils.display(member));
            item.setMemberName(memberName(member, row.getAgentName()));
            item.setOriginalAmount(amount(row.getOriginalCommissionAmount()));
            item.setClawbackAmount(amount(row.getClawbackAmount()));
            item.setDeductedAmount(amount(row.getDeductedAmount()));
            item.setDebtAmount(amount(row.getDebtAmount()));
            item.setTypeName(clawbackTypeName(row.getClawbackType()));
            item.setStatusName(Integer.valueOf(1).equals(row.getStatus()) ? "已完成"
                    : Integer.valueOf(2).equals(row.getStatus()) ? "部分完成" : "待处理");
            item.setReason(row.getReason());
            item.setCreateTime(row.getCreateTime());
            result.add(item);
        }
        return result;
    }

    private void fillAmounts(OrderBonusTraceVO trace,
                             List<DmsCommissionRecord> records,
                             List<DmsBonusCalculationSnapshot> evidence,
                             Map<Long, BigDecimal> clawbackByRecord,
                             Map<Long, BigDecimal> originalByRecord,
                             List<DmsCommissionClawback> clawbacks,
                             List<OrderBonusTraceVO.AssetFlow> assetFlows) {
        BigDecimal calculated = evidence.stream().findFirst().map(DmsBonusCalculationSnapshot::getTotalBonus)
                .orElseGet(() -> originalByRecord.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal settledNet = BigDecimal.ZERO;
        BigDecimal currentNet = BigDecimal.ZERO;
        Set<Long> recipients = new LinkedHashSet<>();
        for (DmsCommissionRecord record : records) {
            CommissionStatusEnum status = CommissionStatusEnum.getByValue(record.getStatus());
            if (status == CommissionStatusEnum.CANCELLED || status == CommissionStatusEnum.REFUNDED) continue;
            recipients.add(record.getAgentId());
            BigDecimal current = amount(record.getCommissionAmount());
            if (status == CommissionStatusEnum.PENDING) {
                pending = pending.add(current);
                currentNet = currentNet.add(current);
            } else if (status == CommissionStatusEnum.SETTLED) {
                BigDecimal net = current.subtract(clawbackByRecord.getOrDefault(record.getId(), BigDecimal.ZERO))
                        .max(BigDecimal.ZERO);
                settledNet = settledNet.add(net);
                currentNet = currentNet.add(net);
            }
        }
        trace.setCalculatedAmount(amount(calculated));
        trace.setPendingAmount(pending);
        trace.setSettledNetAmount(settledNet);
        trace.setCurrentNetAmount(currentNet);
        trace.setRecipientCount(recipients.size());
        trace.setWalletIssuedAmount(assetFlows.stream()
                .filter(item -> "SETTLEMENT".equals(item.getAction()))
                .map(OrderBonusTraceVO.AssetFlow::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        trace.setClawbackAmount(clawbacks.stream().map(DmsCommissionClawback::getClawbackAmount)
                .map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        trace.setDeductedAmount(clawbacks.stream().map(DmsCommissionClawback::getDeductedAmount)
                .map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        trace.setDebtAmount(clawbacks.stream().map(DmsCommissionClawback::getDebtAmount)
                .map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void fillStatus(OrderBonusTraceVO trace,
                            DmsShopOrder order,
                            List<DmsCommissionRecord> records,
                            List<DmsOrderRelationSnapshot> relations,
                            DmsBonusCalculationTask task,
                            List<DmsCommissionClawback> clawbacks) {
        if ("DATA_CONFLICT".equals(trace.getStatus())) return;
        if (Integer.valueOf(0).equals(order.getStatus())) {
            setStatus(trace, "WAITING_PAYMENT", "等待支付", "订单尚未支付，不会冻结关系或产生奖金。");
        } else if (task != null && Integer.valueOf(3).equals(task.getStatus())) {
            setStatus(trace, "CALCULATION_FAILED", "计算失败", "奖金计算任务失败，失败原因和重试次数已保留，请处理后再结算。");
        } else if (records.isEmpty() && relations.isEmpty()) {
            setStatus(trace, "NOT_ENTERED", "未进入奖金程序", "该订单没有冻结推广关系，通常是普通购物账号、商品不计奖或推广资格尚未开通；不代表系统漏发奖金。");
        } else if (records.isEmpty()) {
            setStatus(trace, "NO_PAYOUT", "未产生实际奖金", "订单已冻结关系并进入客户奖金程序，但当前关系与该客户制度没有生成有效收款记录。");
        } else if (trace.getDebtAmount().compareTo(BigDecimal.ZERO) > 0) {
            setStatus(trace, "DEBT_PENDING", "存在待追回金额", "订单退款后的奖金尚未全部追回，剩余金额会按客户项目规则继续抵扣或由后台核对。");
        } else if (clawbacks.stream().allMatch(row -> Integer.valueOf(4).equals(row.getClawbackType()))) {
            setStatus(trace, "DEBT_OFFSET", "已抵扣历史待追回", "本订单原始奖金的一部分已用于归还此前订单的退款待追回金额，实际奖金记录和当前净额均按抵扣后金额展示。");
        } else if (!clawbacks.isEmpty()) {
            setStatus(trace, "REFUND_ADJUSTED", "已发生退款冲销", "实际奖金记录仍完整保留，退款追回、已扣金额和当前净额已在同一链路展示。");
        } else if (trace.getPendingAmount().compareTo(BigDecimal.ZERO) > 0
                && trace.getSettledNetAmount().compareTo(BigDecimal.ZERO) > 0) {
            setStatus(trace, "PARTIALLY_SETTLED", "部分已结算", "部分奖金已进入会员余额，其余奖金仍在等待订单满足结算条件。");
        } else if (trace.getPendingAmount().compareTo(BigDecimal.ZERO) > 0) {
            setStatus(trace, "PENDING_SETTLEMENT", "等待结算", "奖金已生成真实记录，待订单完成且售后冷静期结束后进入会员余额。");
        } else if (trace.getSettledNetAmount().compareTo(BigDecimal.ZERO) > 0) {
            setStatus(trace, "SETTLED", "已结算入账", "奖金已完成结算，实际余额流水和变动前后余额可在下方核对。");
        } else {
            setStatus(trace, "CLOSED", "奖金已关闭", "该订单的奖金记录已取消或已因退款全部冲销，不再形成有效奖金。");
        }
    }

    private List<OrderBonusTraceVO.TimelineEvent> buildTimeline(DmsShopOrder order,
                                                                 List<DmsOrderRelationSnapshot> relations,
                                                                 List<DmsBonusCalculationSnapshot> evidence,
                                                                 DmsBonusCalculationTask task,
                                                                 List<DmsCommissionRecord> records,
                                                                 List<DmsFinanceRefund> refunds,
                                                                 List<DmsCommissionClawback> clawbacks) {
        List<OrderBonusTraceVO.TimelineEvent> events = new ArrayList<>();
        if (order.getPayTime() != null) {
            events.add(event("ORDER_PAID", "订单支付成功", "success", "以支付时的订单金额和业务类型作为奖金入口判断依据。", order.getPayTime()));
        }
        relations.stream().map(DmsOrderRelationSnapshot::getSnapshotTime).filter(Objects::nonNull).min(LocalDateTime::compareTo)
                .ifPresent(time -> events.add(event("RELATION_FROZEN", "推广关系已冻结", "success",
                        "支付时固定了" + relations.size() + "层关系，之后修改上下级不会倒改本订单。", time)));
        if (task != null) {
            String taskTone = Integer.valueOf(3).equals(task.getStatus()) ? "danger"
                    : Integer.valueOf(2).equals(task.getStatus()) ? "success" : "warning";
            events.add(event("CALCULATION_TASK", "奖金计算任务" + taskStatusSuffix(task.getStatus()), taskTone,
                    task.getFailReason() == null ? "任务编号：" + task.getId() : task.getFailReason(),
                    task.getFinishTime() != null ? task.getFinishTime() : task.getCreateTime()));
        } else if (!records.isEmpty()) {
            records.stream().map(DmsCommissionRecord::getCreateTime).filter(Objects::nonNull).min(LocalDateTime::compareTo)
                    .ifPresent(time -> events.add(event("CALCULATED", "支付流程同步完成奖金计算", "success",
                            "生成" + records.size() + "条实际奖金记录。", time)));
        }
        if (!evidence.isEmpty()) {
            DmsBonusCalculationSnapshot latest = evidence.get(0);
            events.add(event("CALCULATION_EVIDENCE", "计算证据已留存",
                    "BLOCK".equals(latest.getRiskStatus()) ? "danger" : "success",
                    "计算奖金合计 ¥" + amount(latest.getTotalBonus()).toPlainString() + "。", latest.getCreateTime()));
        }
        for (DmsCommissionRecord record : records) {
            if (record.getSettleTime() != null) {
                events.add(event("COMMISSION_SETTLED", "奖金结算入账", "success",
                        record.getRecordNo() + "，收款人：" + safe(record.getAgentName())
                                + "，金额 ¥" + amount(record.getCommissionAmount()).toPlainString(), record.getSettleTime()));
            } else if (CommissionStatusEnum.CANCELLED.getValue().equals(record.getStatus())) {
                events.add(event("COMMISSION_CANCELLED", "奖金记录已取消", "info",
                        record.getRecordNo() + "，原因：" + safe(record.getCancelReason()), record.getUpdateTime()));
            }
        }
        for (DmsFinanceRefund refund : refunds) {
            events.add(event("ORDER_REFUND", "订单发生退款", "warning",
                    refund.getRefundNo() + "，退款 ¥" + amount(refund.getRefundAmount()).toPlainString(),
                    refund.getRefundTime() != null ? refund.getRefundTime() : refund.getCreateTime()));
        }
        for (DmsCommissionClawback clawback : clawbacks) {
            boolean debtOffset = Integer.valueOf(4).equals(clawback.getClawbackType());
            events.add(event(debtOffset ? "DEBT_OFFSET" : "COMMISSION_CLAWBACK",
                    debtOffset ? "奖金抵扣历史待追回" : "奖金退款冲销",
                    amount(clawback.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0 ? "danger" : "warning",
                    (debtOffset ? "本次抵扣 ¥" : "应追回 ¥") + amount(clawback.getClawbackAmount()).toPlainString()
                            + "，已冲减 ¥" + amount(clawback.getDeductedAmount()).toPlainString()
                            + "，待追回 ¥" + amount(clawback.getDebtAmount()).toPlainString(), clawback.getCreateTime()));
        }
        return events.stream().filter(item -> item.getTime() != null)
                .sorted(Comparator.comparing(OrderBonusTraceVO.TimelineEvent::getTime))
                .toList();
    }

    private OrderBonusTraceVO.TimelineEvent event(String code, String title, String status,
                                                   String description, LocalDateTime time) {
        OrderBonusTraceVO.TimelineEvent item = new OrderBonusTraceVO.TimelineEvent();
        item.setCode(code);
        item.setTitle(title);
        item.setStatus(status);
        item.setDescription(description);
        item.setTime(time);
        return item;
    }

    private String taskStatusSuffix(Integer status) {
        if (Integer.valueOf(0).equals(status)) return "待处理";
        if (Integer.valueOf(1).equals(status)) return "处理中";
        if (Integer.valueOf(2).equals(status)) return "成功";
        if (Integer.valueOf(3).equals(status)) return "失败";
        return "状态未知";
    }

    private String clawbackTypeName(Integer type) {
        if (Integer.valueOf(1).equals(type)) return "减少待结算奖金";
        if (Integer.valueOf(2).equals(type)) return "从可用余额扣回";
        if (Integer.valueOf(3).equals(type)) return "形成待追回金额";
        if (Integer.valueOf(4).equals(type)) return "由未来奖金抵扣";
        return "其他冲销";
    }

    private void setStatus(OrderBonusTraceVO trace, String status, String statusName, String explanation) {
        trace.setStatus(status);
        trace.setStatusName(statusName);
        trace.setExplanation(explanation);
    }

    private String memberName(DmsShopMember member, String fallback) {
        if (member != null && member.getNickname() != null && !member.getNickname().isBlank()) return member.getNickname();
        return safe(fallback);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
