package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.OrderRelationSnapshotService;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.security.EffectiveMemberPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderRelationSnapshotServiceImpl implements OrderRelationSnapshotService {
    private final DmsOrderRelationSnapshotDao snapshotDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final DmsShopMemberDao memberDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DmsOrderRelationSnapshot> captureDirectReferral(DmsShopOrder order,
            DmsCommissionRuleVersion version, boolean firstPaidOrderEligible) {
        if (order == null || version == null || !Objects.equals(order.getTenantId(), TenantContext.getTenantId())
                || !Objects.equals(order.getTenantId(), version.getTenantId())
                || !CustomerBonusPolicyCodes.DIRECT_REFERRAL.equals(version.getVersionNo()))
            Asserts.fail("直接推荐佣金支付归属上下文不正确");
        List<DmsOrderRelationSnapshot> existing = snapshotDao.selectByOrderId(order.getId());
        if (!existing.isEmpty()) return existing;
        DmsShopMember buyer = memberDao.selectByUserId(order.getUserId());
        if (buyer == null || !Integer.valueOf(1).equals(buyer.getStatus())
                || Integer.valueOf(1).equals(buyer.getSystemAccount())) Asserts.fail("购买账号不可参与佣金归属");
        DmsAgent owner = agentDao.selectByUserId(order.getUserId());
        // 即使没有合格邀请人也冻结版本，后续资格开通或规则变化不能补算旧单。
        DmsOrderRelationSnapshot anchor = directRow(order, version, owner, 0, firstPaidOrderEligible);
        anchor.setTargetUserId(buyer.getUserId());
        anchor.setTargetAgentName(buyer.getNickname());
        anchor.setTargetPromotionEligible(0);
        snapshotDao.insert(anchor);
        if (buyer.getInviterId() != null && !Objects.equals(buyer.getInviterId(), buyer.getUserId())) {
            DmsShopMember inviter = memberDao.selectByUserId(buyer.getInviterId());
            DmsAgent recipient = agentDao.selectByUserId(buyer.getInviterId());
            if (EffectiveMemberPolicy.isActive(inviter, recipient)) {
                DmsOrderRelationSnapshot target = directRow(order, version, owner, 1, firstPaidOrderEligible);
                target.setTargetAgentId(recipient.getId());
                target.setTargetUserId(recipient.getUserId());
                target.setTargetAgentName(recipient.getAgentName());
                target.setTargetPromotionEligible(1);
                target.setRelationPath(buyer.getUserId() + ">" + inviter.getUserId());
                snapshotDao.insert(target);
            }
        }
        return snapshotDao.selectByOrderId(order.getId());
    }

    private DmsOrderRelationSnapshot directRow(DmsShopOrder order, DmsCommissionRuleVersion version,
            DmsAgent owner, int level, boolean firstPaidOrderEligible) {
        DmsOrderRelationSnapshot row = new DmsOrderRelationSnapshot();
        row.setTenantId(order.getTenantId()); row.setRuleVersionId(version.getId());
        row.setOrderId(order.getId()); row.setOrderNo(order.getOrderNo()); row.setOrderUserId(order.getUserId());
        row.setOwnerAgentId(owner == null ? null : owner.getId()); row.setRelationLevel(level);
        row.setFirstPaidOrderEligible(firstPaidOrderEligible ? 1 : 0); row.setSnapshotTime(LocalDateTime.now());
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DmsOrderRelationSnapshot> capture(DmsShopOrder order) {
        List<DmsOrderRelationSnapshot> existing = snapshotDao.selectByOrderId(order.getId());
        if (!existing.isEmpty()) return existing;
        DmsAgent owner = agentDao.selectByUserId(order.getUserId());
        if (owner == null) return List.of();
        DmsCommissionRuleVersion version = ruleVersionDao.selectActiveByTenantIdForUpdate(order.getTenantId());
        insert(order, owner, owner, 0, String.valueOf(owner.getId()), version);
        for (DmsAgentRelation relation : relationDao.selectValidRelationsByUserId(order.getUserId())) {
            if (relation.getParentAgentId() == null || relation.getRelationLevel() == null || relation.getRelationLevel() < 1) continue;
            DmsAgent target = agentDao.selectById(relation.getParentAgentId());
            if (target != null) insert(order, owner, target, relation.getRelationLevel(), relation.getRelationPath(), version);
        }
        return snapshotDao.selectByOrderId(order.getId());
    }

    private void insert(DmsShopOrder order, DmsAgent owner, DmsAgent target, int level, String path,
                        DmsCommissionRuleVersion version) {
        DmsOrderRelationSnapshot row = new DmsOrderRelationSnapshot();
        row.setTenantId(order.getTenantId()); row.setRuleVersionId(version == null ? null : version.getId());
        row.setOrderId(order.getId()); row.setOrderNo(order.getOrderNo()); row.setOrderUserId(order.getUserId());
        row.setOwnerAgentId(owner.getId()); row.setTargetAgentId(target.getId()); row.setTargetUserId(target.getUserId());
        row.setTargetAgentName(target.getAgentName()); row.setRelationLevel(level); row.setRelationPath(path);
        row.setSnapshotTime(LocalDateTime.now()); snapshotDao.insert(row);
    }

    @Override public List<DmsOrderRelationSnapshot> getByOrderId(Long orderId) { return snapshotDao.selectByOrderId(orderId); }
}
