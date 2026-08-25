package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 公开品牌文化页；总开关关闭时不返回正文或详情图。 */
@Data
public class ShopBrandCultureVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean enabled;
    private String brandName;
    private String logoUrl;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String content;
    private List<String> detailImages;
}
