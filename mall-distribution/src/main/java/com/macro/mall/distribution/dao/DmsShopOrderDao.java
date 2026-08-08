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

    DmsShopOrder selectByIdForUpdate(@Param("id") Long id);

    DmsShopOrder selectByOrderNo(@Param("orderNo") String orderNo);

    DmsShopOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<DmsShopOrder> selectByUserId(@Param("userId") Long userId);

    List<DmsShopOrder> selectByUserIdAndState(@Param("userId") Long userId,
                                               @Param("orderState") String orderState);

    ShopOrderStatusSummaryVO selectStatusSummary(@Param("userId") Long userId);

    List<DmsShopOrder> selectByAgentId(@Param("agentId") Long agentId);

    List<DmsShopOrder> selectByMemberUserId(@Param("userId") Long userId);

    List<DmsShopOrder> selectList(@Param("keyword") String keyword,
                                  @Param("status") Integer status,
                                  @Param("orderState") String orderState);

    int insert(DmsShopOrder order);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int markPaid(@Param("id") Long id, @Param("payType") String payType);

    int updateAgentId(@Param("id") Long id, @Param("agentId") Long agentId);

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
