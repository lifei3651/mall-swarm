package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMessageTemplate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsMessageTemplateDao {
    DmsMessageTemplate selectByEventType(@Param("tenantId") Long tenantId, @Param("eventType") String eventType);
    DmsMessageTemplate selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);
    List<DmsMessageTemplate> selectList(@Param("tenantId") Long tenantId);
    int update(DmsMessageTemplate template);
}
