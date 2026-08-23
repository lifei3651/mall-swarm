package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DmsShopOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNo;

    /** 跨商户一次结算的交易父单；历史及单商户订单为空。 */
    private Long tradeId;

    private String tradeNo;

    /** 支付渠道商户单号。跨商户子单为父交易号，历史订单回退到 orderNo。 */
    private String paymentOrderNo;

    private Long tenantId;

    private Long merchantId;

    private String merchantName;

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

    /** 最近24小时内由直播商品点击产生的服务端归因；客户端不能直接指定。 */
    private Long sourceLiveRoomId;

    /** 0待付款，1待发货，2已发货，3已完成，4已关闭（取消、超时关闭或整单退款）。 */
    private Integer status;

    private String payType;

    /** 0未处理、1超时关单后的迟到支付已原路退款。 */
    private Integer lateRefundFlag;

    private String remark;

    /** 客服内部备注，不得直接序列化到会员端。 */
    @JsonIgnore
    private String serviceRemark;

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
