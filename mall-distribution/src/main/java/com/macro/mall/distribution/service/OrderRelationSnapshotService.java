package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsOrderRelationSnapshot;
import com.macro.mall.distribution.entity.DmsShopOrder;
import java.util.List;

public interface OrderRelationSnapshotService {
    List<DmsOrderRelationSnapshot> capture(DmsShopOrder order);
    List<DmsOrderRelationSnapshot> getByOrderId(Long orderId);
}
