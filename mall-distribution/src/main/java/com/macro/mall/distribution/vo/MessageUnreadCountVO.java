package com.macro.mall.distribution.vo;

import lombok.Data;

@Data
public class MessageUnreadCountVO {
    private String category;
    private Long unreadCount;
}
