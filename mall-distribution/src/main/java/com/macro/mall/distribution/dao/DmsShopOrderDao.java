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

    DmsShopOrder selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopOrder selectById(Long id) { return selectByIdScoped(TenantContext.getTenantId(), id); }

    Long selectTradeIdByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default Long selectTradeIdById(Long id) { return selectTradeIdByIdScoped(TenantContext.getTenantId(), id); }

    DmsShopOrder selectByIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopOrder selectByIdForUpdate(Long id) { return selectByIdForUpdateScoped(TenantContext.getTenantId(), id); }

    DmsShopOrder selectByOrderNoScoped(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);
    default DmsShopOrder selectByOrderNo(String orderNo) { return selectByOrderNoScoped(TenantContext.getTenantId(), orderNo); }

    DmsShopOrder selectByOrderNoForUpdateScoped(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);
    default DmsShopOrder selectByOrderNoForUpdate(String orderNo) {
        return selectByOrderNoForUpdateScoped(TenantContext.getTenantId(), orderNo);
    }

    List<DmsShopOrder> selectByTradeIdScoped(@Param("tenantId") Long tenantId, @Param("tradeId") Long tradeId);
    default List<DmsShopOrder> selectByTradeId(Long tradeId) { return selectByTradeIdScoped(TenantContext.getTenantId(), tradeId); }

    List<DmsShopOrder> selectByPaymentOrderNoScoped(@Param("tenantId") Long tenantId,
                                                    @Param("paymentOrderNo") String paymentOrderNo);

    List<DmsShopOrder> selectByTradeIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("tradeId") Long tradeId);
    default List<DmsShopOrder> selectByTradeIdForUpdate(Long tradeId) {
        return selectByTradeIdForUpdateScoped(TenantContext.getTenantId(), tradeId);
    }

    List<DmsShopOrder> selectByUserIdScoped(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    default List<DmsShopOrder> selectByUserId(Long userId) { return selectByUserIdScoped(TenantContext.getTenantId(), userId); }

    List<DmsShopOrder> selectByUserIdAndStateScoped(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                     @Param("orderState") String orderState);
    default List<DmsShopOrder> selectByUserIdAndState(Long userId, String orderState) {
        return selectByUserIdAndStateScoped(TenantContext.getTenantId(), userId, orderState);
    }

    ShopOrderStatusSummaryVO selectStatusSummaryScoped(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    default ShopOrderStatusSummaryVO selectStatusSummary(Long userId) {
        return selectStatusSummaryScoped(TenantContext.getTenantId(), userId);
    }

    ShopOrderStatusSummaryVO selectAdminWorkSummary(@Param("tenantId") Long tenantId,
                                                     @Param("merchantId") Long merchantId);

    default ShopOrderStatusSummaryVO selectAdminWorkSummary(Long tenantId) {
        return selectAdminWorkSummary(tenantId, null);
    }

    List<DmsShopOrder> selectByAgentIdScoped(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    default List<DmsShopOrder> selectByAgentId(Long agentId) { return selectByAgentIdScoped(TenantContext.getTenantId(), agentId); }

    List<DmsShopOrder> selectByMemberUserIdScoped(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    default List<DmsShopOrder> selectByMemberUserId(Long userId) {
        return selectByMemberUserIdScoped(TenantContext.getTenantId(), userId);
    }

    /** 会员全景仅展示已支付且仍属于有效交易或已进入售后的订单。 */
    List<DmsShopOrder> selectPaidProfileOrdersByUserIdScoped(@Param("tenantId") Long tenantId,
                                                              @Param("userId") Long userId);
    default List<DmsShopOrder> selectPaidProfileOrdersByUserId(Long userId) {
        return selectPaidProfileOrdersByUserIdScoped(TenantContext.getTenantId(), userId);
    }

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
    int countValidPaidOrdersByUserIdScoped(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    default int countValidPaidOrdersByUserId(Long userId) {
        return countValidPaidOrdersByUserIdScoped(TenantContext.getTenantId(), userId);
    }

    List<Long> selectExpiredPendingIdsScoped(@Param("tenantId") Long tenantId,
                                             @Param("cutoffTime") LocalDateTime cutoffTime,
                                             @Param("limit") Integer limit);
    default List<Long> selectExpiredPendingIds(LocalDateTime cutoffTime, Integer limit) {
        return selectExpiredPendingIdsScoped(TenantContext.getTenantId(), cutoffTime, limit);
    }

    List<Long> selectExpiredShippedIdsScoped(@Param("tenantId") Long tenantId,
                                             @Param("cutoffTime") LocalDateTime cutoffTime,
                                             @Param("limit") Integer limit);
    default List<Long> selectExpiredShippedIds(LocalDateTime cutoffTime, Integer limit) {
        return selectExpiredShippedIdsScoped(TenantContext.getTenantId(), cutoffTime, limit);
    }

    int closePendingScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int closePending(Long id) { return closePendingScoped(TenantContext.getTenantId(), id); }

    int markLateRefundedScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int markLateRefunded(Long id) { return markLateRefundedScoped(TenantContext.getTenantId(), id); }
}
