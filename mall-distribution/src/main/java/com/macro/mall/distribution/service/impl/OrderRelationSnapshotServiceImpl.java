package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.service.OrderRelationSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderRelationSnapshotServiceImpl implements OrderRelationSnapshotService {
    private final DmsOrderRelationSnapshotDao snapshotDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DmsOrderRelationSnapshot> capture(DmsShopOrder order) {
        List<DmsOrderRelationSnapshot> existing = snapshotDao.selectByOrderId(order.getId());
        if (!existing.isEmpty()) return existing;
        DmsAgent owner = agentDao.selectByUserId(order.getUserId());
        if (owner == null) return List.of();
        DmsCommissionRuleVersion version = ruleVersionDao.selectActiveByTenantId(order.getTenantId());
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
