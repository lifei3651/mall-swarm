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

    /** 审核通过后给客户展示的退货地址快照，避免地址簿修改影响历史售后。 */
    private Long returnAddressId;
    private String returnAddress;

    /** 退货退款流程的客户寄回物流信息。 */
    private String returnDeliveryCompany;
    private String returnDeliveryNo;
    private LocalDateTime returnShippedAt;
    private LocalDateTime returnReceivedAt;

    private Integer status;

    private String auditRemark;

    private Long auditUserId;

    private String auditUserName;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 下一责任方与处理时限为运行时派生字段，不落库，也不代表超时自动退款。 */
    private String nextActionParty;
    private LocalDateTime nextActionDeadline;
    private Boolean nextActionOverdue;
    private String nextActionHint;
}
