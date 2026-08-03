package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsProductPvConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsProductPvConfigDao {

    DmsProductPvConfig selectById(@Param("id") Long id);

    List<DmsProductPvConfig> selectList(@Param("tenantId") Long tenantId,
                                        @Param("keyword") String keyword,
                                        @Param("status") Integer status);

    int insert(DmsProductPvConfig config);

    int update(DmsProductPvConfig config);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);
}
