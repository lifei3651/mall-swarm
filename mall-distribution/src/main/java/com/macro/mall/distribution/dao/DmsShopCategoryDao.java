package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsShopCategoryDao {

    DmsShopCategory selectById(@Param("id") Long id);

    List<DmsShopCategory> selectList(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    int insert(DmsShopCategory category);

    int update(DmsShopCategory category);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
