package com.macro.mall.mapper;

import com.macro.mall.model.PmsProductTag;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PmsProductTagMapper {
    int deleteByPrimaryKey(Long id);
    int insert(PmsProductTag row);
    int insertSelective(PmsProductTag row);
    PmsProductTag selectByPrimaryKey(Long id);
    List<PmsProductTag> selectAll();
    List<PmsProductTag> selectByStatus(@Param("status") Integer status);
    int updateByPrimaryKeySelective(PmsProductTag row);
    int updateByPrimaryKey(PmsProductTag row);
}
