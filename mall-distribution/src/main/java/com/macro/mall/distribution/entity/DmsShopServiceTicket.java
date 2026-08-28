package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopServiceTicket implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ticketNo;
    @JsonIgnore
    private Long tenantId;
    @JsonIgnore
    private Long merchantId;
    @JsonIgnore
    private Long memberId;
    @JsonIgnore
    private Long userId;
    private String type;
    private String subject;
    private String status;
    private Long orderId;
    private String orderNo;
    private Long afterSaleId;
    private String afterSaleNo;
    @JsonIgnore
    private Long assignedAdminId;
    @JsonIgnore
    private String assignedAdminName;
    private String lastReplyBy;
    private LocalDateTime lastReplyTime;
    private LocalDateTime firstResponseDeadline;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedTime;
    private LocalDateTime closedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 仅用于页面展示，不落库。 */
    private String memberAccount;
    private String handlerName;
    private Boolean firstResponseOverdue;
    private String nextActionParty;
    private String nextActionHint;
}
