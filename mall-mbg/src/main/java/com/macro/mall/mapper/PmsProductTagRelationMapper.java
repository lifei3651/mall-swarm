package com.macro.mall.mapper;

import com.macro.mall.model.PmsProductTagRelation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PmsProductTagRelationMapper {
    int deleteByProductId(@Param("productId") Long productId);
    int deleteByTagId(@Param("tagId") Long tagId);
    int insert(PmsProductTagRelation row);
    int insertBatch(@Param("list") List<PmsProductTagRelation> list);
    List<PmsProductTagRelation> selectByProductId(@Param("productId") Long productId);
    List<PmsProductTagRelation> selectByTagId(@Param("tagId") Long tagId);
    List<Long> selectTagIdsByProductId(@Param("productId") Long productId);
    List<Long> selectProductIdsByTagId(@Param("tagId") Long tagId);
}
