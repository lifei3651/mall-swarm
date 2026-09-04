package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMiniProgramSubscriptionGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsMiniProgramSubscriptionGrantDao {
    int insertIgnore(DmsMiniProgramSubscriptionGrant grant);
    int countAvailable(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                       @Param("templateIdHash") String templateIdHash);
    DmsMiniProgramSubscriptionGrant selectReservedByTask(@Param("tenantId") Long tenantId,
                                                          @Param("taskId") Long taskId);
    DmsMiniProgramSubscriptionGrant selectAvailableForUpdate(@Param("tenantId") Long tenantId,
                                                              @Param("memberId") Long memberId,
                                                              @Param("templateIdHash") String templateIdHash);
    int reserve(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("taskId") Long taskId);
    int markConsumed(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId);
    int markInvalid(@Param("tenantId") Long tenantId, @Param("taskId") Long taskId);
}
