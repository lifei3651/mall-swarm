package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsLineChangeApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsLineChangeApplicationDao {
    DmsLineChangeApplication selectById(@Param("id") Long id);
    DmsLineChangeApplication selectPendingByAgentId(@Param("agentId") Long agentId);
    List<Long> selectPendingAgentIds(@Param("agentIds") List<Long> agentIds);
    List<DmsLineChangeApplication> selectList(@Param("status") Integer status);
    List<DmsLineChangeApplication> selectDueApproved(@Param("now") LocalDateTime now);
    int insert(DmsLineChangeApplication application);
    int audit(@Param("id") Long id, @Param("status") Integer status,
              @Param("auditorId") Long auditorId, @Param("auditorName") String auditorName,
              @Param("auditRemark") String auditRemark, @Param("auditTime") LocalDateTime auditTime);
    int markExecuted(@Param("id") Long id, @Param("afterSnapshot") String afterSnapshot,
                     @Param("executeTime") LocalDateTime executeTime);
    int markDirectExecuted(@Param("id") Long id, @Param("operatorId") Long operatorId,
                           @Param("operatorName") String operatorName, @Param("operationRemark") String operationRemark,
                           @Param("afterSnapshot") String afterSnapshot, @Param("executeTime") LocalDateTime executeTime);
}
