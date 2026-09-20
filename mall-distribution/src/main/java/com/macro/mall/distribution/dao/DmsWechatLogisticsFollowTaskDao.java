package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWechatLogisticsFollowTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsWechatLogisticsFollowTaskDao {

    int enqueue(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                @Param("shipmentId") Long shipmentId, @Param("userId") Long userId);

    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    DmsWechatLogisticsFollowTask selectById(@Param("id") Long id);

    int markSuccess(@Param("id") Long id, @Param("owner") String owner,
                    @Param("payloadDigest") String payloadDigest,
                    @Param("now") LocalDateTime now);

    int markRetry(@Param("id") Long id, @Param("owner") String owner,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("errorCode") String errorCode);

    int markTerminal(@Param("id") Long id, @Param("owner") String owner,
                     @Param("status") String status, @Param("errorCode") String errorCode,
                     @Param("now") LocalDateTime now);

    int completeInteractive(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                            @Param("shipmentId") Long shipmentId, @Param("userId") Long userId,
                            @Param("payloadDigest") String payloadDigest,
                            @Param("now") LocalDateTime now);
}
