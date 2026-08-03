package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导入批次Mapper接口
 */
@Mapper
public interface DmsImportBatchDao {

    /**
     * 根据ID查询批次
     */
    DmsImportBatch selectById(@Param("id") Long id);

    /**
     * 根据批次编号查询
     */
    DmsImportBatch selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 查询所有批次
     */
    List<DmsImportBatch> selectAll();

    /**
     * 根据导入类型查询批次
     */
    List<DmsImportBatch> selectByImportType(@Param("importType") Integer importType);

    /**
     * 根据状态查询批次
     */
    List<DmsImportBatch> selectByStatus(@Param("status") Integer status);

    /**
     * 插入批次
     */
    int insert(DmsImportBatch batch);

    /**
     * 更新批次
     */
    int update(DmsImportBatch batch);

    /**
     * 更新批次状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新批次统计
     */
    int updateCounts(@Param("id") Long id, @Param("successCount") Integer successCount, @Param("failCount") Integer failCount);

    /**
     * 删除批次
     */
    int deleteById(@Param("id") Long id);
}
