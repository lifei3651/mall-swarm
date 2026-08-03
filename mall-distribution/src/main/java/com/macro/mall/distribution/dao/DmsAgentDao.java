package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAgent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;
import com.macro.mall.distribution.vo.AgentInfoVO;

/**
 * 代理Mapper接口
 */
@Mapper
public interface DmsAgentDao {

    /**
     * 根据ID查询代理
     */
    DmsAgent selectById(@Param("id") Long id);

    /**
     * 根据用户ID查询代理
     */
    DmsAgent selectByUserId(@Param("userId") Long userId);

    /**
     * 根据代理编号查询代理
     */
    DmsAgent selectByAgentCode(@Param("agentCode") String agentCode);

    /**
     * 根据邀请码查询代理
     */
    DmsAgent selectByInviteCode(@Param("inviteCode") String inviteCode);

    /**
     * 查询所有代理
     */
    List<DmsAgent> selectAll();

    /** 查询没有推广上级的根代理，用于默认展示完整关系树。 */
    List<DmsAgent> selectRoots();

    /**
     * 根据关键词查询代理
     */
    List<DmsAgent> search(@Param("keyword") String keyword, @Param("status") Integer status);

    /** 按关键词、状态和级别组合查询，用于后台列表及完整导出。 */
    List<DmsAgent> searchWithLevel(@Param("keyword") String keyword,
                                   @Param("status") Integer status,
                                   @Param("agentLevel") Integer agentLevel);

    /**
     * 根据上级代理ID查询下级代理列表
     */
    List<DmsAgent> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 根据状态查询代理列表
     */
    List<DmsAgent> selectByStatus(@Param("status") Integer status);

    /** 批量查询关系树节点的当月业绩、累计奖金和有效团队人数。 */
    List<AgentInfoVO> selectTreeMetrics(@Param("agentIds") List<Long> agentIds,
                                        @Param("monthStart") LocalDateTime monthStart);

    /**
     * 插入代理
     */
    int insert(DmsAgent agent);

    /**
     * 更新代理
     */
    int update(DmsAgent agent);

    /**
     * 更新代理状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除代理（逻辑删除）
     */
    int deleteById(@Param("id") Long id);

    /** 物理删除代理记录，用于后台“取消会员资格（调整为非会员）”。 */
    int hardDeleteById(@Param("id") Long id);
}
