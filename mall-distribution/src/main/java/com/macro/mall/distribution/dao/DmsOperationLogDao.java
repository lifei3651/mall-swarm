package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOperationLogDao {

    List<DmsOperationLog> selectList(@Param("moduleName") String moduleName,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") String targetId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    int insert(DmsOperationLog log);

    List<Long> selectIdsBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    int deleteByIds(@Param("ids") List<Long> ids);
}
