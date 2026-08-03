package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsAgentChangeLogDao;
import com.macro.mall.distribution.dao.DmsOrderPerformanceDetailDao;
import com.macro.mall.distribution.dao.DmsMigrationBaselineDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentChangeLog;
import com.macro.mall.distribution.entity.DmsMigrationBaseline;
import com.macro.mall.distribution.enums.ChangeTypeEnum;
import com.macro.mall.distribution.enums.AgentStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

/** 新零售简版自动调级器：新增订单只升级，退款可按冲销后的条件降级，移线本身不触发调级。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewRetailRankService {

    private final DmsAgentDao agentDao;
    private final DmsAgentChangeLogDao changeLogDao;
    private final DmsOrderPerformanceDetailDao performanceDetailDao;
    private final DmsMigrationBaselineDao migrationBaselineDao;

    @Transactional(rollbackFor = Exception.class)
    public void refreshAllRanks() {
        refreshRanks(false, "新增有效订单", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void refreshAllRanksAfterRefund(Long orderId, Long refundId) {
        // 退款只可能改变订单本人及支付快照中各级上级的件数、业绩和部门资格。
        // 严禁全员降级，否则不相关的后台调级/外部迁入会员也可能被这一笔退款误伤。
        Set<Long> affectedAgentIds = performanceDetailDao.selectByOrderId(orderId).stream()
                .map(item -> item.getTargetAgentId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (affectedAgentIds.isEmpty()) return;
        refreshRanks(true, "退款冲销：orderId=" + orderId + ", refundId=" + refundId, affectedAgentIds);
    }

    private void refreshRanks(boolean allowDowngrade, String trigger, Set<Long> scopedAgentIds) {
        List<DmsAgent> agents = agentDao.selectAll();
        // 下级先算，保证下级卡级变化能在同一轮传递给上级部门条件。
        agents.sort(Comparator.comparing(DmsAgent::getLevelDepth,
                Comparator.nullsFirst(Integer::compareTo)).reversed());
        Map<Long, DmsAgent> byId = agents.stream().collect(Collectors.toMap(DmsAgent::getId, item -> item));
        // 反复计算，确保最多八个卡级的连锁变化稳定后再返回。
        for (int round = 0; round < 8; round++) {
            boolean changed = false;
            for (DmsAgent agent : agents) {
                if (scopedAgentIds != null && !scopedAgentIds.contains(agent.getId())) continue;
                if (!AgentStatusEnum.NORMAL.getValue().equals(agent.getStatus())) continue;
                int target = targetRank(agent, agents, byId);
                int current = agent.getAgentLevel() == null ? 1 : agent.getAgentLevel();
                if (target > current || (allowDowngrade && target < current)) {
                    agent.setAgentLevel(target);
                    agentDao.update(agent);
                    recordAutomaticLevelChange(agent, current, target, trigger);
                    changed = true;
                    log.info("新零售自动调级: agentId={}, {} -> {}, trigger={}", agent.getId(), current, target, trigger);
                }
            }
            if (!changed) return;
        }
    }

    private int targetRank(DmsAgent agent, List<DmsAgent> all, Map<Long, DmsAgent> byId) {
        DmsMigrationBaseline baseline = migrationBaselineDao.selectByAgentId(agent.getId());
        // 本人和无限层团队的每件商品都计1单；退款以负件数冲销。
        int orders = performanceDetailDao.sumEffectiveTeamUnits(agent.getId())
                + (baseline == null || baseline.getHistoricalOrderCount() == null ? 0 : baseline.getHistoricalOrderCount());
        // 直推只统计 parent_id 等于本人的第一代有效会员；孙级及更深层级不能计入。
        List<DmsAgent> direct = all.stream()
                .filter(item -> agent.getId().equals(item.getParentId()))
                .filter(item -> AgentStatusEnum.NORMAL.getValue().equals(item.getStatus()))
                .toList();
        int rank = 1;
        if (orders >= 10) rank = 2;
        if (rank >= 2 && direct.size() >= 5 && orders >= 50) rank = 3;
        if (rank >= 3 && orders >= 150 && direct.stream().filter(item -> level(item) >= 2).count() >= 3) rank = 4;
        if (rank >= 4 && orders >= 500 && qualifiedDepartments(direct, all, byId, 4) >= 2) rank = 5;
        if (rank >= 5 && qualifiedDepartments(direct, all, byId, 5) >= 2) rank = 6;
        if (rank >= 6 && qualifiedDepartments(direct, all, byId, 6) >= 2) rank = 7;
        if (rank >= 7 && qualifiedDepartments(direct, all, byId, 7) >= 2) rank = 8;
        return rank;
    }

    private long qualifiedDepartments(List<DmsAgent> direct, List<DmsAgent> all, Map<Long, DmsAgent> byId, int requiredRank) {
        return direct.stream().filter(branch -> all.stream()
                .filter(candidate -> AgentStatusEnum.NORMAL.getValue().equals(candidate.getStatus()))
                .anyMatch(candidate -> isInBranch(candidate, branch.getId(), byId) && level(candidate) >= requiredRank)).count();
    }

    private boolean isInBranch(DmsAgent candidate, Long branchRootId, Map<Long, DmsAgent> byId) {
        DmsAgent current = candidate;
        while (current != null) {
            if (branchRootId.equals(current.getId())) return true;
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return false;
    }

    private int level(DmsAgent agent) {
        return agent.getAgentLevel() == null ? 1 : agent.getAgentLevel();
    }

    private void recordAutomaticLevelChange(DmsAgent agent, int oldLevel, int newLevel, String trigger) {
        DmsAgentChangeLog change = new DmsAgentChangeLog();
        change.setAgentId(agent.getId());
        change.setUserId(agent.getUserId());
        change.setChangeType(newLevel > oldLevel
                ? ChangeTypeEnum.UPGRADE.getValue() : ChangeTypeEnum.DOWNGRADE.getValue());
        change.setOldLevel(oldLevel);
        change.setNewLevel(newLevel);
        change.setChangeReason("新零售规则自动调级：" + rankName(oldLevel) + " → " + rankName(newLevel));
        change.setChangeDetail("{\"mode\":\"automatic\",\"basis\":\"self_and_unlimited_team_product_units_and_direct_referral_conditions\",\"trigger\":\""
                + trigger.replace("\"", "'") + "\",\"historyRecalculated\":false}");
        change.setOperatorId(0L);
        change.setOperatorName("system");
        change.setOperatorType(1);
        changeLogDao.insert(change);
    }

    private String rankName(int rank) {
        return switch (rank) {
            case 2 -> "VIP会员";
            case 3 -> "店铺";
            case 4 -> "代理";
            case 5 -> "一星董事";
            case 6 -> "二星董事";
            case 7 -> "三星董事";
            case 8 -> "合伙人";
            default -> "会员";
        };
    }
}
