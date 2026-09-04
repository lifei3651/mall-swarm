package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsWechatShippingSyncTaskDao {
    int enqueue(@Param("tenantId") Long tenantId, @Param("paymentOrderNo") String paymentOrderNo,
                @Param("userId") Long userId);
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    DmsWechatShippingSyncTask selectById(@Param("id") Long id);
    int markSuccess(@Param("id") Long id, @Param("owner") String owner,
                    @Param("revision") Integer revision, @Param("payloadDigest") String payloadDigest,
                    @Param("now") LocalDateTime now);
    int markRetry(@Param("id") Long id, @Param("owner") String owner,
                  @Param("revision") Integer revision, @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("errorCode") String errorCode);
    int markPermanent(@Param("id") Long id, @Param("owner") String owner,
                      @Param("revision") Integer revision, @Param("errorCode") String errorCode,
                      @Param("now") LocalDateTime now);
}
