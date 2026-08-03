package com.macro.mall.mapper;

import com.macro.mall.model.UmsLoginLog;
import java.util.List;

public interface UmsLoginLogMapper {
    int insert(UmsLoginLog record);

    UmsLoginLog selectByPrimaryKey(Long id);

    List<UmsLoginLog> selectAll();

    int updateByPrimaryKey(UmsLoginLog record);

    int deleteByPrimaryKey(Long id);
}
