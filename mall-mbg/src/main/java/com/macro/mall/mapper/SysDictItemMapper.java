package com.macro.mall.mapper;

import com.macro.mall.model.SysDictItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysDictItemMapper {
    int insert(SysDictItem row);
    int updateByPrimaryKey(SysDictItem row);
    int deleteByPrimaryKey(Long id);
    int deleteByGroupCode(@Param("groupCode") String groupCode);
    SysDictItem selectByPrimaryKey(Long id);
    List<SysDictItem> selectByGroupCode(@Param("groupCode") String groupCode);
    List<SysDictItem> selectAll();
}
