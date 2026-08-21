package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.vo.ShopOrderStatusSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface DmsShopOrderDao {

    DmsShopOrder selectById(@Param("id") Long id);

    Long selectTradeIdById(@Param("id") Long id);

    DmsShopOrder selectByIdForUpdate(@Param("id") Long id);

    DmsShopOrder selectByOrderNo(@Param("orderNo") String orderNo);

    DmsShopOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<DmsShopOrder> selectByTradeId(@Param("tradeId") Long tradeId);

    List<DmsShopOrder> selectByTradeIdForUpdate(@Param("tradeId") Long tradeId);

    List<DmsShopOrder> selectByUserId(@Param("userId") Long userId);

    List<DmsShopOrder> selectByUserIdAndState(@Param("userId") Long userId,
                                               @Param("orderState") String orderState);

    ShopOrderStatusSummaryVO selectStatusSummary(@Param("userId") Long userId);

    ShopOrderStatusSummaryVO selectAdminWorkSummary(@Param("tenantId") Long tenantId,
                                                     @Param("merchantId") Long merchantId);

    default ShopOrderStatusSummaryVO selectAdminWorkSummary(Long tenantId) {
        return selectAdminWorkSummary(tenantId, null);
    }

    List<DmsShopOrder> selectByAgentId(@Param("agentId") Long agentId);

    List<DmsShopOrder> selectByMemberUserId(@Param("userId") Long userId);

    /** 会员全景仅展示已支付且仍属于有效交易或已进入售后的订单。 */
    List<DmsShopOrder> selectPaidProfileOrdersByUserId(@Param("userId") Long userId);

    List<DmsShopOrder> selectList(@Param("tenantId") Long tenantId,
                                  @Param("keyword") String keyword,
                                  @Param("status") Integer status,
                                  @Param("orderState") String orderState,
                                  @Param("merchantId") Long merchantId);

    default List<DmsShopOrder> selectList(String keyword, Integer status, String orderState) {
        return selectList(TenantContext.getTenantId(), keyword, status, orderState, null);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("order") DmsShopOrder order);
    default int insert(DmsShopOrder order) {
        Long tenantId = TenantContext.getTenantId();
        if (order == null) throw new IllegalArgumentException("订单不能为空");
        if (order.getTenantId() == null) order.setTenantId(tenantId);
        if (!tenantId.equals(order.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的订单");
        return insertScoped(tenantId, order);
    }

    int updateStatusScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") Integer status);
    default int updateStatus(Long id, Integer status) { return updateStatusScoped(TenantContext.getTenantId(), id, status); }

    int markPaidScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("payType") String payType);
    default int markPaid(Long id, String payType) { return markPaidScoped(TenantContext.getTenantId(), id, payType); }

    int updateAgentIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("agentId") Long agentId);
    default int updateAgentId(Long id, Long agentId) { return updateAgentIdScoped(TenantContext.getTenantId(), id, agentId); }

    int updateServiceRemarkScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                  @Param("serviceRemark") String serviceRemark);
    default int updateServiceRemark(Long id, String serviceRemark) {
        return updateServiceRemarkScoped(TenantContext.getTenantId(), id, serviceRemark);
    }

    int shipScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                   @Param("deliveryCompany") String deliveryCompany,
                   @Param("deliveryNo") String deliveryNo);
    default int ship(Long id, String deliveryCompany, String deliveryNo) {
        return shipScoped(TenantContext.getTenantId(), id, deliveryCompany, deliveryNo);
    }

    int confirmReceiveScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int confirmReceive(Long id) { return confirmReceiveScoped(TenantContext.getTenantId(), id); }

    int cancelScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int cancel(Long id) { return cancelScoped(TenantContext.getTenantId(), id); }

    int closeAfterSaleScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int closeAfterSale(Long id) { return closeAfterSaleScoped(TenantContext.getTenantId(), id); }

    /** 查询会员名下仍有效的支付订单数（已支付/已发货/已收货，不含已全额退款关闭的订单）。 */
    int countValidPaidOrdersByUserId(@Param("userId") Long userId);

    List<Long> selectExpiredPendingIds(@Param("cutoffTime") LocalDateTime cutoffTime,
                                       @Param("limit") Integer limit);

    int closePendingScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int closePending(Long id) { return closePendingScoped(TenantContext.getTenantId(), id); }
}
