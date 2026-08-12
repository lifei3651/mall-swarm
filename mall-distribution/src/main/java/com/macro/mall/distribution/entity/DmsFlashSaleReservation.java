package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsFlashSaleReservation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long activityId;
    private Long userId;
    private Long orderId;
    private String orderNo;
    private Integer quantity;
    private Integer releasedQuantity;
    /** RESERVED、PAID、RELEASED。 */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
