package com.macro.mall.mapper;

import com.macro.mall.model.UmsMemberLevel;
import java.util.List;

public interface UmsMemberLevelMapper {
    int insert(UmsMemberLevel record);

    UmsMemberLevel selectByPrimaryKey(Long id);

    List<UmsMemberLevel> selectAll();

    int updateByPrimaryKey(UmsMemberLevel record);

    int deleteByPrimaryKey(Long id);
}
