package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderPerformanceDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单业绩明细Mapper接口
 */
@Mapper
public interface DmsOrderPerformanceDetailDao {

    /**
     * 根据ID查询明细
     */
    DmsOrderPerformanceDetail selectById(@Param("id") Long id);

    /**
     * 根据订单ID查询明细
     */
    List<DmsOrderPerformanceDetail> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单归属用户ID查询明细
     */
    List<DmsOrderPerformanceDetail> selectByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    /**
     * 根据目标代理ID查询明细
     */
    List<DmsOrderPerformanceDetail> selectByTargetAgentId(@Param("targetAgentId") Long targetAgentId);

    /**
     * 查询代理的团队业绩明细
     */
    List<DmsOrderPerformanceDetail> selectTeamPerformanceDetails(@Param("targetAgentId") Long targetAgentId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询代理的个人业绩明细
     */
    List<DmsOrderPerformanceDetail> selectPersonalPerformanceDetails(@Param("ownerAgentId") Long ownerAgentId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询下属贡献的具体订单明细
     */
    List<DmsOrderPerformanceDetail> selectSubordinateOrderDetails(@Param("targetAgentId") Long targetAgentId, @Param("ownerAgentId") Long ownerAgentId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    int sumEffectiveTeamUnits(@Param("agentId") Long agentId);

    /**
     * 插入明细
     */
    int insert(DmsOrderPerformanceDetail detail);

    /**
     * 批量插入明细
     */
    int insertBatch(@Param("list") List<DmsOrderPerformanceDetail> list);

    /**
     * 更新明细
     */
    int update(DmsOrderPerformanceDetail detail);

    /**
     * 更新明细状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除明细
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新业绩归属（切线时使用）
     * 将所有历史业绩的target从旧上级更新为新上级
     */
    int updateTargetAgentId(@Param("ownerUserId") Long ownerUserId,
                            @Param("oldTargetAgentId") Long oldTargetAgentId,
                            @Param("newTargetAgentId") Long newTargetAgentId,
                            @Param("newTargetAgentName") String newTargetAgentName);

    /**
     * 查询代理及其所有下级的用户ID列表
     */
    List<Long> selectDescendantUserIds(@Param("agentId") Long agentId);
}
