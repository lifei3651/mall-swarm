package com.macro.mall.distribution.dao;

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
        return selectList(1L, keyword, status, orderState, null);
    }

    int insert(DmsShopOrder order);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int markPaid(@Param("id") Long id, @Param("payType") String payType);

    int updateAgentId(@Param("id") Long id, @Param("agentId") Long agentId);

    int updateServiceRemark(@Param("id") Long id, @Param("serviceRemark") String serviceRemark);

    int ship(@Param("id") Long id,
             @Param("deliveryCompany") String deliveryCompany,
             @Param("deliveryNo") String deliveryNo);

    int confirmReceive(@Param("id") Long id);

    int cancel(@Param("id") Long id);

    int closeAfterSale(@Param("id") Long id);

    /** 查询会员名下仍有效的支付订单数（已支付/已发货/已收货，不含已全额退款关闭的订单）。 */
    int countValidPaidOrdersByUserId(@Param("userId") Long userId);

    List<Long> selectExpiredPendingIds(@Param("cutoffTime") LocalDateTime cutoffTime,
                                       @Param("limit") Integer limit);

    int closePending(@Param("id") Long id);
}
