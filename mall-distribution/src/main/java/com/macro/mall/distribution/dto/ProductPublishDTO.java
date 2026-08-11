package com.macro.mall.distribution.dto;

import com.macro.mall.distribution.entity.DmsShopProduct;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

/** 商品主体与 SKU 一次事务发布，避免只保存一半。 */
@Data
public class ProductPublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Valid
    @NotNull(message = "商品信息不能为空")
    private DmsShopProduct product;

    @Size(max = 200, message = "单个商品最多维护200个SKU")
    private List<@Valid ShopSkuDTO> skus;

    @Size(max = 200, message = "单次最多删除200个SKU")
    private List<Long> removedSkuIds;
}
