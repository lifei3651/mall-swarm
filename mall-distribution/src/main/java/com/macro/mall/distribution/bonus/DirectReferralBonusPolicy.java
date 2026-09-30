package com.macro.mall.distribution.bonus;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsOrderRelationSnapshotDao;
import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.service.DirectReferralConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** 普通购买者也可为有推广资格的直接邀请人产生佣金，绝不越级。 */
@Component
@RequiredArgsConstructor
public class DirectReferralBonusPolicy implements CustomerBonusPolicy {
    public static final String PAYOUT_CODE = "DIRECT_REFERRAL";
    private final DirectReferralConfigService configService;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;

    @Override
    public String policyCode() {
        return CustomerBonusPolicyCodes.DIRECT_REFERRAL;
    }

    @Override
    public List<CustomerBonusPayout> calculate(CustomerBonusOrderContext context) {
        DirectReferralRuleConfig config = configService.frozen(context.tenantId(), context.ruleVersionId());
        if (!Boolean.TRUE.equals(config.enabled()) || context.bonusBaseAmount() == null
                || context.bonusBaseAmount().compareTo(BigDecimal.ZERO) <= 0) return List.of();
        List<DmsOrderRelationSnapshot> snapshots = relationSnapshotDao.selectByOrderId(context.orderId());
        List<DmsOrderRelationSnapshot> anchors = snapshots.stream()
                .filter(item -> Integer.valueOf(0).equals(item.getRelationLevel())).toList();
        if (anchors.size() != 1 || !matches(anchors.get(0), context)) {
            Asserts.fail("直接推荐佣金缺少完整支付归属快照");
        }
        if (DirectReferralRuleConfig.FIRST_PAID_ORDER.equals(config.purchaseScope())) {
            if (!Integer.valueOf(0).equals(anchors.get(0).getFirstPaidOrderEligible())
                    && !Integer.valueOf(1).equals(anchors.get(0).getFirstPaidOrderEligible())) {
                Asserts.fail("直接推荐佣金缺少首次支付资格快照");
            }
            if (!Integer.valueOf(1).equals(anchors.get(0).getFirstPaidOrderEligible())) return List.of();
        }
        List<DmsOrderRelationSnapshot> targets = snapshots.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getRelationLevel())).toList();
        if (targets.isEmpty()) return List.of();
        if (targets.size() != 1 || !matches(targets.get(0), context)) {
            Asserts.fail("直接推荐佣金的邀请人快照不正确");
        }
        DmsOrderRelationSnapshot target = targets.get(0);
        if (!Integer.valueOf(0).equals(target.getTargetPromotionEligible())
                && !Integer.valueOf(1).equals(target.getTargetPromotionEligible())) {
            Asserts.fail("直接推荐佣金缺少邀请人推广资格快照");
        }
        if (!Integer.valueOf(1).equals(target.getTargetPromotionEligible())
                || target.getTargetAgentId() == null || target.getTargetUserId() == null
                || Objects.equals(target.getTargetUserId(), context.orderUserId())) return List.of();
        BigDecimal amount = context.bonusBaseAmount().multiply(config.commissionRate())
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) return List.of();
        return List.of(new CustomerBonusPayout(target.getTargetAgentId(), 1, PAYOUT_CODE,
                config.commissionRate(), amount, "直接推荐成交佣金（支付时归属与规则）"));
    }

    private boolean matches(DmsOrderRelationSnapshot item, CustomerBonusOrderContext context) {
        return Objects.equals(item.getTenantId(), context.tenantId())
                && Objects.equals(item.getRuleVersionId(), context.ruleVersionId())
                && Objects.equals(item.getOrderId(), context.orderId())
                && Objects.equals(item.getOrderUserId(), context.orderUserId());
    }
}
