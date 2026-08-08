package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商城经营地址：用于商品发货或售后退货，不绑定具体会员。 */
@Data
public class DmsShopServiceAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    /** 1=发货地址，2=退货地址。 */
    private Integer addressType;
    private String addressLabel;
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
