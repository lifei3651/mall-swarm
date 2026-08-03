package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShopProductDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DmsShopProduct product;

    private List<DmsShopSku> skus;

    private DmsTenantDisplayConfig displayConfig;
}
