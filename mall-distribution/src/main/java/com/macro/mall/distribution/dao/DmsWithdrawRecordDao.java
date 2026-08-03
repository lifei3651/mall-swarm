package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsWithdrawRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提现记录Mapper接口
 */
@Mapper
public interface DmsWithdrawRecordDao {

    /**
     * 根据ID查询记录
     */
    DmsWithdrawRecord selectById(@Param("id") Long id);

    DmsWithdrawRecord selectByIdForUpdate(@Param("id") Long id);

    /**
     * 根据提现单号查询
     */
    DmsWithdrawRecord selectByWithdrawNo(@Param("withdrawNo") String withdrawNo);

    /**
     * 根据代理ID查询提现记录
     */
    List<DmsWithdrawRecord> selectByAgentId(@Param("agentId") Long agentId);

    /**
     * 根据状态查询提现记录
     */
    List<DmsWithdrawRecord> selectByStatus(@Param("status") Integer status);

    /**
     * 查询所有提现记录
     */
    List<DmsWithdrawRecord> selectAll();

    /**
     * 按条件查询提现记录
     */
    List<DmsWithdrawRecord> search(@Param("agentId") Long agentId,
                                   @Param("status") Integer status,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 插入记录
     */
    int insert(DmsWithdrawRecord record);

    /**
     * 更新记录
     */
    int update(DmsWithdrawRecord record);

    /**
     * 更新记录状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除记录
     */
    int deleteById(@Param("id") Long id);
}
