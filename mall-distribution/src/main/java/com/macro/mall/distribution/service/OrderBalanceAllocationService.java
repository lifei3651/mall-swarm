package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;

import java.util.List;

public interface OrderBalanceAllocationService {

    String PRODUCT_COST = "PRODUCT_COST";
    String REMAINDER = "REMAINDER";

    List<DmsOrderBalanceAllocation> prepareForOrder(Long orderId);

    int prepareMissingOrders(int limit);

    int settleEligibleAfterCoolingOff(int limit);

    void recalculateAfterRefund(Long orderId, Long refundId);

    List<DmsOrderBalanceAllocation> listByOrderId(Long orderId);
}
