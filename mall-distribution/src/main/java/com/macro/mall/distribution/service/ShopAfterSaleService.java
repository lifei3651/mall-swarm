package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleReturnShipmentDTO;
import com.macro.mall.distribution.dto.ShopManualRefundDTO;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopMember;

import java.util.List;

public interface ShopAfterSaleService {

    DmsShopAfterSale apply(DmsShopMember member, ShopAfterSaleApplyDTO dto);

    /** 会员撤回尚未审核的售后申请，不产生退款或账务变动。 */
    DmsShopAfterSale cancel(DmsShopMember member, Long id);

    /** 客户提交退货物流，售后进入等待商家收货状态。 */
    DmsShopAfterSale submitReturnShipment(DmsShopMember member, Long id, ShopAfterSaleReturnShipmentDTO dto);

    List<DmsShopAfterSale> listByMember(DmsShopMember member);

    List<DmsShopAfterSale> listAdmin(String keyword, Integer status);

    DmsShopAfterSale audit(Long id, ShopAfterSaleAuditDTO dto);

    /** 商家确认收到退货后，执行退款及库存、财务、奖金冲销。 */
    DmsShopAfterSale confirmReturnReceived(Long id, ShopAfterSaleAuditDTO dto);

    /** 后台在前台售后期限结束后登记并执行退款。 */
    DmsShopAfterSale manualRefund(Long orderId, ShopManualRefundDTO dto);

    /** 后台取消待发货订单：全额退款、冲销账务并恢复预占库存。 */
    boolean cancelPendingShipment(Long orderId, Long operatorId, String operatorName);
}
