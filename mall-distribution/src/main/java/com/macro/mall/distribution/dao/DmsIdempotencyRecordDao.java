package com.macro.mall.distribution.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsIdempotencyRecordDao {
    int insertProcessing(@Param("requestKey") String requestKey);

    int markSucceeded(@Param("requestKey") String requestKey);

    int deleteProcessing(@Param("requestKey") String requestKey);
}
