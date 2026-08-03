package com.macro.mall.mapper;

import com.macro.mall.model.SysDictGroup;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysDictGroupMapper {
    int insert(SysDictGroup row);
    int updateByPrimaryKey(SysDictGroup row);
    int deleteByPrimaryKey(Long id);
    SysDictGroup selectByPrimaryKey(Long id);
    SysDictGroup selectByGroupCode(@Param("groupCode") String groupCode);
    List<SysDictGroup> selectAll();
}
