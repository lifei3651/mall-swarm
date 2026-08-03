package com.macro.mall.mapper;

import com.macro.mall.model.UmsMemberTag;
import java.util.List;

public interface UmsMemberTagMapper {
    int insert(UmsMemberTag record);

    UmsMemberTag selectByPrimaryKey(Long id);

    List<UmsMemberTag> selectAll();

    int updateByPrimaryKey(UmsMemberTag record);

    int deleteByPrimaryKey(Long id);
}
