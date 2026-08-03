package com.macro.mall.distribution.dto;

import com.macro.mall.distribution.entity.DmsShopProduct;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 商品主体与 SKU 一次事务发布，避免只保存一半。 */
@Data
public class ProductPublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DmsShopProduct product;
    private List<ShopSkuDTO> skus;
    private List<Long> removedSkuIds;
}
