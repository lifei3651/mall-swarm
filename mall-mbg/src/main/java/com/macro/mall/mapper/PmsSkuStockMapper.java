package com.macro.mall.mapper;

import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.PmsSkuStockExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PmsSkuStockMapper {
    long countByExample(PmsSkuStockExample example);

    int deleteByExample(PmsSkuStockExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PmsSkuStock row);

    int insertSelective(PmsSkuStock row);

    List<PmsSkuStock> selectByExample(PmsSkuStockExample example);

    PmsSkuStock selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") PmsSkuStock row, @Param("example") PmsSkuStockExample example);

    int updateByExample(@Param("row") PmsSkuStock row, @Param("example") PmsSkuStockExample example);

    int updateByPrimaryKeySelective(PmsSkuStock row);

    int updateByPrimaryKey(PmsSkuStock row);

    /**
     * 原子锁定库存（防止超卖）
     * @param id SKU ID
     * @param quantity 要锁定的数量
     * @return 影响行数，0表示库存不足
     */
    int lockStockAtomic(@Param("id") Long id, @Param("quantity") Integer quantity);
}