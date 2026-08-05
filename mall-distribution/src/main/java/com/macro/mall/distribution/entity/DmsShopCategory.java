package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long tenantId;

    private String categoryName;

    private String iconUrl;

    private Integer sort;

    private Integer status;

    /** 是否在首页展示：1-展示，0-隐藏 */
    private Integer showOnHome;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
