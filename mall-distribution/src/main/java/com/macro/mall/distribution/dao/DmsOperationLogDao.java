package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsOperationLogDao {

    List<DmsOperationLog> selectList(@Param("moduleName") String moduleName,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") String targetId);

    int insert(DmsOperationLog log);
}
