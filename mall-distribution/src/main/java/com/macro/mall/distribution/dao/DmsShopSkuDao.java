package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopSkuDao {

    DmsShopSku selectById(@Param("id") Long id);

    List<DmsShopSku> selectByProductId(@Param("productId") Long productId,
                                       @Param("status") Integer status);

    int insert(DmsShopSku sku);

    int update(DmsShopSku sku);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
