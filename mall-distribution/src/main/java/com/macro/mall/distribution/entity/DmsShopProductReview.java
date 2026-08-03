package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商品真实购买评价及后台展示状态。 */
@Data
public class DmsShopProductReview implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long productId;
    private String productName;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long userId;
    private String reviewerName;
    private String reviewerAvatar;
    private Integer rating;
    private String content;
    /** 0-隐藏，1-展示。 */
    private Integer status;
    private String hiddenReason;
    private Long hiddenBy;
    private String hiddenByName;
    private LocalDateTime hiddenTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
