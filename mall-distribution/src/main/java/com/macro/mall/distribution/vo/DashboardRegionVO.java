package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 工作台会员收货地区分布。 */
@Data
public class DashboardRegionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String regionName;
    private Long memberCount;
    /** 在已设置有效收货地址会员中的占比，0-100。 */
    private BigDecimal percentage;
}
