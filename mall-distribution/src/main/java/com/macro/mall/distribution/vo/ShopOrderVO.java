package com.macro.mall.distribution.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** 本次一次支付使用的父交易ID；单商户订单为空并继续使用 order.id。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long checkoutId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String checkoutNo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean groupedCheckout;

    /** 仅跨商户提交结果返回各商户履约子单。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ShopOrderVO> childOrders;

    /** 下单人的登录账号。 */
    private String memberAccount;

    /** 仅后台订单列表填充；会员端响应不包含该字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String serviceRemark;

    /** 仅商户后台订单返回；false 表示历史订单已由平台接管或履约被冻结。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean merchantFulfillmentAllowed;

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
    private Boolean afterSaleSelfServiceEnabled;

    /** 当前订单的自动确认收货规则；只在已完整发货且尚未确认收货时生成截止时间。 */
    private Integer autoReceiveDays;
    private LocalDateTime autoReceiveDeadline;
    private Boolean autoReceiveEnabled;

    private DmsTenantDisplayConfig displayConfig;
}
