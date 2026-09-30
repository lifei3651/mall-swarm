package com.macro.mall.distribution.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 永久首笔成功支付标记，按商城和顾客隔离，不因退款清除。 */
@Mapper
public interface DmsMemberFirstPaymentDao {
    int claim(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("orderId") Long orderId);
    Long selectFirstOrderForUpdate(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
