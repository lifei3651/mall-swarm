package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopProductDao {

    DmsShopProduct selectById(@Param("id") Long id);

    List<DmsShopProduct> selectList(@Param("tenantId") Long tenantId,
                                    @Param("keyword") String keyword,
                                    @Param("categoryName") String categoryName,
                                    @Param("status") Integer status,
                                    @Param("stockStatus") String stockStatus);

    List<String> selectCategories(@Param("tenantId") Long tenantId);

    int insert(DmsShopProduct product);

    int update(DmsShopProduct product);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateCategoryName(@Param("tenantId") Long tenantId,
                           @Param("oldName") String oldName,
                           @Param("newName") String newName);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
