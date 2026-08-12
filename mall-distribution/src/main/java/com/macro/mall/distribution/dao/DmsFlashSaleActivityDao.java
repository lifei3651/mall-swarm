package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsFlashSaleActivityDao {
    DmsFlashSaleActivity selectById(@Param("id") Long id);
    List<DmsFlashSaleActivity> selectList(@Param("tenantId") Long tenantId, @Param("status") Integer status);
    List<DmsFlashSaleActivity> selectFrontList(@Param("tenantId") Long tenantId);
    int insert(DmsFlashSaleActivity activity);
    int update(DmsFlashSaleActivity activity);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
