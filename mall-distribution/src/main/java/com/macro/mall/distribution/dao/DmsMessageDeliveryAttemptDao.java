package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageDeliveryAttempt;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DmsMessageDeliveryAttemptDao {
    int insert(DmsMessageDeliveryAttempt attempt);
    DmsMessageDeliveryAttempt selectLatest(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId);
    List<DmsMessageDeliveryAttempt> selectByTask(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId);
    int updateResult(@Param("tenantId") Long tenantId, @Param("id") Long id,
                     @Param("state") String state, @Param("providerMessageId") String providerMessageId,
                     @Param("actualCost") BigDecimal actualCost, @Param("errorCode") String errorCode,
                     @Param("errorMessage") String errorMessage, @Param("resolvedTime") LocalDateTime resolvedTime);
    int incrementQuery(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
