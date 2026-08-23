package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsLiveAnchor implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    private Long memberUserId;
    private String displayName;
    /** PRODUCT 厂家商品、PLATFORM 平台讲解、FACTORY 工厂常态。 */
    private String anchorType;
    private String companyName;
    private String bio;
    /** 1 可开播、2 暂停、3 已收回。 */
    private Integer status;
    private LocalDateTime lastLiveTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
