package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人中心订单状态数量汇总。
 *
 * <p>只返回状态数量，避免个人中心为了角标加载全部订单详情。</p>
 */
@Data
public class ShopOrderStatusSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total = 0L;

    private Long pendingPayment = 0L;

    private Long pendingShipment = 0L;

    private Long pendingReceipt = 0L;

    private Long pendingReview = 0L;

    private Long afterSale = 0L;
}
