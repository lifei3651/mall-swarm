package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.PerformanceQueryDTO;
import com.macro.mall.distribution.vo.OrderPerformanceDetailVO;
import com.macro.mall.distribution.vo.PerformanceOverviewVO;
import com.macro.mall.distribution.vo.PerformanceRankingVO;
import com.macro.mall.distribution.vo.SubordinateContributionVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 业绩统计服务接口
 */
public interface PerformanceService {

    /**
     * 将后台输入的登录账号或手机号解析为内部关系ID。
     */
    Long resolveAgentId(String keyword);

    /**
     * 记录订单业绩（订单完成后调用）
     * @param orderId 订单ID
     * @param orderNo 订单编号
     * @param orderAmount 订单金额
     * @param orderUserId 下单用户ID
     * @param orderTime 下单时间
     */
    void recordOrderPerformance(Long orderId, String orderNo, java.math.BigDecimal orderAmount,
                                Integer quantity, Long orderUserId, java.time.LocalDateTime orderTime);

    /**
     * 为退款生成反向业绩流水。原始业绩不改写，冲销作为独立流水保留审计证据。
     */
    void reverseOrderPerformance(Long orderId, Long refundId, java.math.BigDecimal productRefundAmount,
                                 Integer refundQuantity,
                                 java.time.LocalDateTime refundTime);

    /**
     * 查询代理的业绩概览
     * @param agentId 代理ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 业绩概览
     */
    PerformanceOverviewVO getPerformanceOverview(Long agentId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询代理的团队成员贡献列表
     * @param agentId 代理ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 贡献列表
     */
    List<SubordinateContributionVO> getSubordinateContributions(Long agentId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询某个下属贡献的具体订单明细
     * @param agentId 代理ID
     * @param subordinateAgentId 下属代理ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 订单明细
     */
    List<OrderPerformanceDetailVO> getSubordinateOrderDetails(Long agentId, Long subordinateAgentId,
                                                                LocalDate startDate, LocalDate endDate);

    /** 查询目标代理在时间范围内全部业绩来源（个人订单、团队订单及退款冲正） */
    List<OrderPerformanceDetailVO> getPerformanceSourceDetails(Long agentId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询业绩排行
     * @param rankType 排名类型：1-个人业绩 2-团队业绩 3-新增代理
     * @param rankPeriod 排名周期：1-日 2-周 3-月 4-年
     * @param statDate 统计日期
     * @return 排行列表
     */
    List<PerformanceRankingVO> getPerformanceRanking(Integer rankType, Integer rankPeriod, LocalDate statDate);

    /**
     * 刷新业绩汇总（定时任务调用）
     * @param statDate 统计日期
     */
    void refreshDailySummary(LocalDate statDate);

    /**
     * 刷新月度业绩汇总
     * @param statDate 统计日期（月份中的任意一天）
     */
    void refreshMonthlySummary(LocalDate statDate);

    /**
     * 切线时更新业绩归属
     * 将指定代理及其所有下级的历史业绩归属从旧上级更新为新上级
     * @param agentId 被切线的代理ID
     * @param oldParentAgentId 旧上级代理ID
     * @param newParentAgentId 新上级代理ID
     */
    void updatePerformanceOnSwitchLine(Long agentId, Long oldParentAgentId, Long newParentAgentId);
}
