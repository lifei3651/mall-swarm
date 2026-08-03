package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 前台评价视图，不暴露会员、订单和后台审核字段。 */
@Data
public class ProductReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String reviewerName;
    private String reviewerAvatar;
    private Integer rating;
    private String content;
    private LocalDateTime createTime;
}
