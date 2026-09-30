package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import java.util.List;

public interface OrderRelationSnapshotService {
    List<DmsOrderRelationSnapshot> capture(DmsShopOrder order);
    List<DmsOrderRelationSnapshot> captureDirectReferral(DmsShopOrder order, DmsCommissionRuleVersion version,
                                                      boolean firstPaidOrderEligible);
    List<DmsOrderRelationSnapshot> getByOrderId(Long orderId);
}
