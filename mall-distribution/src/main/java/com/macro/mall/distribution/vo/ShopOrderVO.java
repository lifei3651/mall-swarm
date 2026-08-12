package com.macro.mall.distribution.vo;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ShopOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DmsShopOrder order;

    /** 下单人的登录账号。 */
    private String memberAccount;

    private List<DmsShopOrderItem> items;

    /** 物流包裹；支持一个订单多包裹，以及同一包裹关联多张订单。 */
    private List<DmsShopOrderShipment> shipments;

    private OrderFinanceVO finance;

    private List<DmsShopAfterSale> afterSales;

    /** 已完成订单中尚未评价的商品明细数量。 */
    private Integer pendingReviewCount;

    /** 当前订单适用的客户售后入口规则，由服务端统一计算，前端不得自行猜测。 */
    private String afterSaleWindowMode;
    private Integer afterSaleWindowDays;
    private String afterSaleWindowLabel;
    private LocalDateTime afterSaleDeadline;

    private DmsTenantDisplayConfig displayConfig;
}
