package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWechatExpressOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsWechatExpressOrderDao {
    DmsWechatExpressOrder selectByRequest(@Param("tenantId") Long tenantId,
                                          @Param("orderId") Long orderId,
                                          @Param("requestKey") String requestKey);

    DmsWechatExpressOrder selectByShipmentId(@Param("tenantId") Long tenantId,
                                             @Param("shipmentId") Long shipmentId);

    DmsWechatExpressOrder selectByShipmentIdForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("orderId") Long orderId,
                                                      @Param("shipmentId") Long shipmentId);

    int insertIgnore(DmsWechatExpressOrder order);

    int markWaybill(@Param("tenantId") Long tenantId, @Param("id") Long id,
                    @Param("waybillId") String waybillId);

    int markSuccess(@Param("tenantId") Long tenantId, @Param("id") Long id,
                    @Param("shipmentId") Long shipmentId);

    int markFailed(@Param("tenantId") Long tenantId, @Param("id") Long id,
                   @Param("errorCode") String errorCode);

    int markCancelling(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int markCancelConfirmed(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int markCancelled(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int restoreCancelFailure(@Param("tenantId") Long tenantId, @Param("id") Long id,
                             @Param("errorCode") String errorCode);
}
