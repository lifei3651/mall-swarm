package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopServiceTicketReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    @JsonIgnore
    private Long tenantId;
    private Long ticketId;
    private String senderType;
    @JsonIgnore
    private Long senderId;
    @JsonIgnore
    private String senderName;
    private String senderLabel;
    private String content;
    private LocalDateTime createTime;
}
