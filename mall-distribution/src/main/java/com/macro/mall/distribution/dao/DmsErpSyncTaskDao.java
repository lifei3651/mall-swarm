package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsErpSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsErpSyncTaskDao {
    DmsErpSyncTask selectById(@Param("id") Long id);
    DmsErpSyncTask selectByUnique(@Param("integrationId") Long integrationId, @Param("bizType") String bizType, @Param("bizId") String bizId);
    List<DmsErpSyncTask> selectList(@Param("integrationId") Long integrationId, @Param("status") Integer status);
    List<DmsErpSyncTask> selectRetryable(@Param("now") LocalDateTime now,
                                         @Param("limit") Integer limit,
                                         @Param("maxRetryCount") Integer maxRetryCount);
    int insert(DmsErpSyncTask entity);
    int markSuccess(@Param("id") Long id, @Param("response") String response);
    int stopExceededRetries(@Param("maxRetryCount") Integer maxRetryCount);
    int markFailure(@Param("id") Long id,
                    @Param("status") Integer status,
                    @Param("retryCount") Integer retryCount,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime,
                    @Param("error") String error);
}
