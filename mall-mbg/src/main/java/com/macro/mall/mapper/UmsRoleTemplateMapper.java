package com.macro.mall.mapper;

import com.macro.mall.model.UmsRoleTemplate;
import java.util.List;

public interface UmsRoleTemplateMapper {
    int insert(UmsRoleTemplate record);

    UmsRoleTemplate selectByPrimaryKey(Long id);

    List<UmsRoleTemplate> selectAll();

    int updateByPrimaryKey(UmsRoleTemplate record);

    int deleteByPrimaryKey(Long id);
}
