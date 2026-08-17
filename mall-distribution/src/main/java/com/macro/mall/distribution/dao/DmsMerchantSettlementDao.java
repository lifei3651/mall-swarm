package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsMerchantSettlementDao {
    DmsMerchantSettlement selectByIdForUpdate(@Param("id") Long id);
    DmsMerchantSettlement selectByOrderItemIdForUpdate(@Param("orderItemId") Long orderItemId);
    List<DmsMerchantSettlement> selectByOrderId(@Param("orderId") Long orderId);
    List<DmsMerchantSettlement> selectList(@Param("tenantId") Long tenantId,
                                           @Param("merchantId") Long merchantId,
                                           @Param("status") String status);
    List<Long> selectPendingOrderIds(@Param("limit") Integer limit);
    int insert(DmsMerchantSettlement settlement);
    int markAvailable(@Param("id") Long id);
    int applyReversal(@Param("id") Long id, @Param("quantity") Integer quantity,
                      @Param("amount") BigDecimal amount, @Param("status") String status);
}
