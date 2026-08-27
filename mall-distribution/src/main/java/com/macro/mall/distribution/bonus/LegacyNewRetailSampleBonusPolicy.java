package com.macro.mall.distribution.bonus;

import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.enums.AgentStatusEnum;
import com.macro.mall.distribution.service.impl.NewRetailBonusPolicy;
import com.macro.mall.distribution.service.impl.NewRetailRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仅为既有测试数据保留的历史示例程序。
 *
 * <p>新客户不得把它当作默认制度；客户派生项目应新增自己的
 * {@link CustomerBonusPolicy} 实现并使用独立规则版本号。</p>
 */
@Component
@RequiredArgsConstructor
public class LegacyNewRetailSampleBonusPolicy implements CustomerBonusPolicy {

    private final DmsAgentDao agentDao;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;
    private final NewRetailRankService rankService;

    @Override
    public String policyCode() {
        return NewRetailBonusPolicy.VERSION_NO;
    }

    @Override
    public List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context) {
        DmsAgent orderAgent = agentDao.selectByUserId(context.orderUserId());
        if (orderAgent == null) return List.of();

        List<DmsOrderRelationSnapshot> snapshots = relationSnapshotDao.selectByOrderId(context.orderId());
        Map<Long, DmsAgent> payoutRankSnapshot = new LinkedHashMap<>();
        snapshots.stream()
                .filter(item -> item.getRelationLevel() != null && item.getRelationLevel() >= 1)
                .forEach(item -> payoutRankSnapshot.computeIfAbsent(item.getTargetAgentId(), agentDao::selectById));

        List<CustomerBonusPayout> payouts = new ArrayList<>();
        snapshots.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getRelationLevel()))
                .findFirst()
                .ifPresent(item -> {
                    DmsAgent inviter = payoutRankSnapshot.get(item.getTargetAgentId());
                    if (isNormal(inviter)) {
                        add(payouts, context, inviter, 1, NewRetailBonusPolicy.DIRECT_REWARD,
                                NewRetailBonusPolicy.directRate(inviter.getAgentLevel()),
                                "历史示例直推奖（按本单支付前卡级）");
                    }
                });

        Set<Integer> paidLevels = new HashSet<>();
        Set<Long> paidReceiverIds = new HashSet<>();
        snapshots.stream()
                .filter(item -> item.getRelationLevel() != null && item.getRelationLevel() >= 1)
                .sorted(Comparator.comparing(DmsOrderRelationSnapshot::getRelationLevel))
                .forEach(item -> {
                    DmsAgent receiver = payoutRankSnapshot.get(item.getTargetAgentId());
                    if (!isNormal(receiver) || !paidReceiverIds.add(receiver.getId())) return;
                    BigDecimal rate = NewRetailBonusPolicy.directorShareRate(receiver.getAgentLevel());
                    if (rate.compareTo(BigDecimal.ZERO) <= 0 || !paidLevels.add(receiver.getAgentLevel())) return;
                    add(payouts, context, receiver, item.getRelationLevel(), NewRetailBonusPolicy.DIRECTOR_SHARE,
                            rate, "历史示例团队分红（按本单支付前卡级，关系层级" + item.getRelationLevel() + "）");
                });
        return payouts;
    }

    @Override
    public void afterOrder(CustomerBonusOrderContext context) {
        // 历史示例保持原口径：本单奖金先冻结支付前卡级，再刷新后续订单使用的卡级。
        rankService.refreshRanksAfterOrder(context.orderId());
    }

    @Override
    public void afterRefund(CustomerBonusRefundContext context) {
        rankService.refreshAllRanksAfterRefund(context.orderId(), context.refundId());
    }

    private void add(List<CustomerBonusPayout> payouts, CustomerBonusOrderContext context,
                     DmsAgent receiver, Integer relationshipLevel, String bonusCode,
                     BigDecimal rate, String remark) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal amount = context.bonusBaseAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        payouts.add(new CustomerBonusPayout(receiver.getId(), relationshipLevel, bonusCode, rate, amount, remark));
    }

    private boolean isNormal(DmsAgent agent) {
        return agent != null && AgentStatusEnum.NORMAL.getValue().equals(agent.getStatus());
    }
}
