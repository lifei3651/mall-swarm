package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import com.macro.mall.distribution.notification.ExternalNotificationContext;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface DmsMessageDeliveryTaskDao {
    int insertIgnore(DmsMessageDeliveryTask task);
    List<DmsMessageDeliveryTask> selectList(@Param("tenantId") Long tenantId,
                                            @Param("channel") String channel,
                                            @Param("status") String status);
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    DmsMessageDeliveryTask selectById(@Param("id") Long id);
    DmsMessageDeliveryTask selectByIdForUpdate(@Param("id") Long id);
    ExternalNotificationContext selectContext(@Param("id") Long id);
    int incrementAttempt(@Param("id") Long id, @Param("owner") String owner);
    int markFinal(@Param("id") Long id, @Param("owner") String owner, @Param("status") String status,
                  @Param("providerCode") String providerCode, @Param("providerMessageId") String providerMessageId,
                  @Param("actualCost") BigDecimal actualCost, @Param("errorCode") String errorCode,
                  @Param("errorMessage") String errorMessage, @Param("resolvedTime") LocalDateTime resolvedTime);
    int markRetryable(@Param("id") Long id, @Param("owner") String owner,
                      @Param("retryCount") int retryCount, @Param("nextRetryTime") LocalDateTime nextRetryTime,
                      @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);
    int markUnknown(@Param("id") Long id, @Param("owner") String owner,
                    @Param("providerCode") String providerCode, @Param("providerMessageId") String providerMessageId,
                    @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                    @Param("leaseUntil") LocalDateTime leaseUntil);
    int applyReceipt(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("status") String status,
                     @Param("providerCode") String providerCode, @Param("providerMessageId") String providerMessageId,
                     @Param("errorCode") String errorCode, @Param("resolvedTime") LocalDateTime resolvedTime);
    int scheduleAcceptedQuery(@Param("id") Long id, @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
