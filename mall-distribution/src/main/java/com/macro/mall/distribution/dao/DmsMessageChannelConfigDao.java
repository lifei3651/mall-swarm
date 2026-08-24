package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsMessageChannelConfigDao {
    DmsMessageChannelConfig selectByEventType(@Param("tenantId") Long tenantId, @Param("eventType") String eventType);
    DmsMessageChannelConfig selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);
    List<DmsMessageChannelConfig> selectList(@Param("tenantId") Long tenantId);
    int updateInApp(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("enabled") Integer enabled);
}
