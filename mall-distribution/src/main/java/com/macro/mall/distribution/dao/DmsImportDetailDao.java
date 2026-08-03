package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsImportDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导入详情Mapper接口
 */
@Mapper
public interface DmsImportDetailDao {

    /**
     * 根据ID查询详情
     */
    DmsImportDetail selectById(@Param("id") Long id);

    /**
     * 根据批次ID查询详情
     */
    List<DmsImportDetail> selectByBatchId(@Param("batchId") Long batchId);

    /**
     * 根据批次ID和状态查询详情
     */
    List<DmsImportDetail> selectByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") Integer status);

    /**
     * 插入详情
     */
    int insert(DmsImportDetail detail);

    /**
     * 批量插入详情
     */
    int insertBatch(@Param("list") List<DmsImportDetail> list);

    /**
     * 更新详情
     */
    int update(DmsImportDetail detail);

    /**
     * 更新详情状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("errorMsg") String errorMsg, @Param("targetId") Long targetId);

    /**
     * 删除详情
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据批次ID删除详情
     */
    int deleteByBatchId(@Param("batchId") Long batchId);
}
