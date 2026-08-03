package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsFreightTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsFreightTemplateDao {

    DmsFreightTemplate selectById(@Param("id") Long id);

    List<DmsFreightTemplate> selectList(@Param("tenantId") Long tenantId,
                                        @Param("status") Integer status);

    int insert(DmsFreightTemplate template);

    int update(DmsFreightTemplate template);
}
