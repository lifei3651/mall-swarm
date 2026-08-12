package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import lombok.Data;

import java.io.Serializable;

@Data
public class FlashSaleActivityVO implements Serializable {
    private DmsFlashSaleActivity activity;
    private DmsShopProduct product;
    private DmsShopSku sku;
    /** UPCOMING、ACTIVE、SOLD_OUT、ENDED、DISABLED。 */
    private String activityState;
}
