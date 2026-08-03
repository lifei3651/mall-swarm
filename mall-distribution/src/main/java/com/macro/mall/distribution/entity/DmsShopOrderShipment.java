package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单与物流包裹的关联记录。
 *
 * <p>一张订单可以关联多个包裹；同一个包裹也可以关联多张订单（合箱发货）。</p>
 */
@Data
public class DmsShopOrderShipment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private Long orderId;

    private String orderNo;

    private String deliveryCompany;

    private String deliveryNo;

    /** 本包裹对应当前订单的发货件数。 */
    private Integer shipmentQuantity;

    private String source;

    private LocalDateTime deliveryTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
