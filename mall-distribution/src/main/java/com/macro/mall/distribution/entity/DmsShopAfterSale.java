package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DmsShopAfterSale implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String afterSaleNo;

    private Long orderId;

    private String orderNo;

    private Long memberId;

    private Long userId;

    /** 仅用于后台返回，不落库。 */
    private String memberAccount;

    private Integer applyType;

    private BigDecimal refundAmount;

    private BigDecimal productRefundAmount;

    private BigDecimal freightRefundAmount;

    private Integer refundQuantity;

    private List<DmsShopAfterSaleItem> items;

    private String reason;

    private String proofImages;

    private Integer status;

    private String auditRemark;

    private Long auditUserId;

    private String auditUserName;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
