package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsShopOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNo;

    private Long tenantId;

    private Long userId;

    private Long agentId;

    private String inviteCode;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String receiverProvince;

    private String receiverCity;

    private String receiverDistrict;

    private String receiverDetailAddress;

    private BigDecimal totalAmount;

    private BigDecimal freightAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    private BigDecimal totalPv;

    private BigDecimal totalCost;

    /** NORMAL普通订单、FLASH_SALE秒杀订单、REPURCHASE复购订单。 */
    private String businessType;

    /** 秒杀活动等业务来源ID；普通和复购订单为空。 */
    private Long businessSourceId;

    /** 0待付款，1待发货，2已发货，3已完成，4已关闭（取消、超时关闭或整单退款）。 */
    private Integer status;

    private String payType;

    private String remark;

    private LocalDateTime payTime;

    private String deliveryCompany;

    private String deliveryNo;

    private LocalDateTime deliveryTime;

    private LocalDateTime receiveTime;

    private LocalDateTime cancelTime;

    private LocalDateTime closeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
