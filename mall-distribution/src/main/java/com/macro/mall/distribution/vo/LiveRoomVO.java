package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsLiveRoom;
import com.macro.mall.distribution.entity.DmsShopProduct;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LiveRoomVO implements Serializable {

    private DmsLiveRoom room;
    private String roomState;
    private List<DmsShopProduct> products;
    private List<Long> productIds;
}
