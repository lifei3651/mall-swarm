package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageDeliveryTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsMessageDeliveryTaskDao {
    int insertIgnore(DmsMessageDeliveryTask task);
    List<DmsMessageDeliveryTask> selectList(@Param("tenantId") Long tenantId,
                                            @Param("channel") String channel,
                                            @Param("status") String status);
}
