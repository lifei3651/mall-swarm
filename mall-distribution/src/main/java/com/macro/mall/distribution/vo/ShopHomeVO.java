package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopBanner;
import com.macro.mall.distribution.entity.DmsShopCategory;
import com.macro.mall.distribution.entity.DmsShopNotice;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShopHomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String brandName;

    /** 商城品牌 Logo，由后台“商城设置”维护。 */
    private String logoUrl;

    private String themeColor;

    private String productTemplate;

    private List<String> categories;

    private List<DmsShopCategory> categoryList;

    private List<DmsShopBanner> banners;

    private List<DmsShopNotice> notices;

    private List<DmsShopProduct> featuredProducts;

    private DistributionSettingsVO distributionSettings;

    private DmsTenantDisplayConfig displayConfig;

    /** 法律与客服信息，用于首页信任条展示。 */
    private ShopLegalConfigVO legalConfig;

    /** 秒杀、复购等可选业务入口；关闭时前台不展示。 */
    private ShopBusinessConfigVO businessConfig;
}
