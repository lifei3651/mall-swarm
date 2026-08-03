package com.macro.mall.mapper;

import com.macro.mall.model.OmsReturnType;
import java.util.List;

public interface OmsReturnTypeMapper {
    int insert(OmsReturnType record);

    OmsReturnType selectByPrimaryKey(Long id);

    List<OmsReturnType> selectAll();

    int updateByPrimaryKey(OmsReturnType record);

    int deleteByPrimaryKey(Long id);
}
