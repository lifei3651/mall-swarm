package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.StatTypeEnum;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import com.macro.mall.distribution.service.AgentRelationService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.vo.OrderPerformanceDetailVO;
import com.macro.mall.distribution.vo.PerformanceOverviewVO;
import com.macro.mall.distribution.vo.PerformanceRankingVO;
import com.macro.mall.distribution.vo.SubordinateContributionVO;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 业绩统计服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceServiceImpl implements PerformanceService {

    private final DmsOrderPerformanceDetailDao performanceDetailDao;
    private final DmsAgentPerformanceSummaryDao summaryDao;
    private final DmsSubordinateContributionDao contributionDao;
    private final DmsAgentDao agentDao;
    private final DmsShopMemberDao shopMemberDao;
    private final DmsAgentAccountDao agentAccountDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsOrderRelationSnapshotDao relationSnapshotDao;
    private final AgentRelationService relationService;

    @Override
    public Long resolveAgentId(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            Asserts.fail("请输入登录账号或手机号");
        }
        String value = keyword.trim();
        DmsShopMember member = shopMemberDao.selectByAccount(value);
        if (member != null) {
            DmsAgent memberAgent = agentDao.selectByUserId(member.getUserId());
            if (memberAgent == null) {
                Asserts.fail("该商城账号尚未进入会员关系和业绩体系");
            }
            return memberAgent.getId();
        }
        Asserts.fail("未找到对应会员，请使用登录账号或手机号查询");
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordOrderPerformance(Long orderId, String orderNo, BigDecimal orderAmount,
                                       Integer quantity, Long orderUserId, LocalDateTime orderTime) {
        // 查询下单用户是否是代理
        DmsAgent orderAgent = agentDao.selectByUserId(orderUserId);
        if (orderAgent == null) {
            log.info("下单用户不是代理，不记录业绩: userId={}", orderUserId);
            return;
        }

        int effectiveUnits = quantity == null || quantity < 1 ? 1 : quantity;

        // 1. 记录个人业绩（relation_level = 0）。累计单量按商品件数，不按订单张数。
        DmsOrderPerformanceDetail personalDetail = new DmsOrderPerformanceDetail();
        personalDetail.setOrderId(orderId);
        personalDetail.setOrderNo(orderNo);
        personalDetail.setOrderAmount(orderAmount);
        personalDetail.setOrderTime(orderTime);
        personalDetail.setOwnerUserId(orderUserId);
        personalDetail.setOwnerAgentId(orderAgent.getId());
        personalDetail.setOwnerAgentName(orderAgent.getAgentName());
        personalDetail.setTargetAgentId(orderAgent.getId());
        personalDetail.setTargetAgentName(orderAgent.getAgentName());
        personalDetail.setRelationLevel(0);
        personalDetail.setQuantity(effectiveUnits);
        personalDetail.setProductAmount(orderAmount);
        personalDetail.setPerformanceType(1); // 个人业绩
        personalDetail.setPerformanceAmount(orderAmount);
        personalDetail.setStatus(1);
        performanceDetailDao.insert(personalDetail);
        agentAccountDao.addTotalOrders(orderAgent.getId(), effectiveUnits);

        // 2. 只使用订单支付时冻结的关系快照，禁止读取可能已移线的当前关系。
        List<DmsOrderRelationSnapshot> relations = relationSnapshotDao.selectByOrderId(orderId);
        for (DmsOrderRelationSnapshot relation : relations) {
            if (relation.getRelationLevel() == null || relation.getRelationLevel() < 1) continue;
            DmsAgent parentAgent = agentDao.selectById(relation.getTargetAgentId());
            if (parentAgent == null) {
                continue;
            }

            DmsOrderPerformanceDetail teamDetail = new DmsOrderPerformanceDetail();
            teamDetail.setOrderId(orderId);
            teamDetail.setOrderNo(orderNo);
            teamDetail.setOrderAmount(orderAmount);
            teamDetail.setOrderTime(orderTime);
            teamDetail.setOwnerUserId(orderUserId);
            teamDetail.setOwnerAgentId(orderAgent.getId());
            teamDetail.setOwnerAgentName(orderAgent.getAgentName());
            teamDetail.setTargetAgentId(parentAgent.getId());
            teamDetail.setTargetAgentName(parentAgent.getAgentName());
            teamDetail.setRelationLevel(relation.getRelationLevel());
            teamDetail.setQuantity(effectiveUnits);
            teamDetail.setProductAmount(orderAmount);
            teamDetail.setPerformanceType(2); // 团队业绩
            teamDetail.setPerformanceAmount(orderAmount);
            teamDetail.setStatus(1);
            performanceDetailDao.insert(teamDetail);

            // 更新下属贡献记录
            agentAccountDao.addTotalOrders(parentAgent.getId(), effectiveUnits);
            updateSubordinateContribution(parentAgent.getId(), orderAgent.getId(), orderAmount,
                    effectiveUnits, relation.getRelationLevel(), orderTime.toLocalDate());
        }

        log.info("记录订单业绩成功: orderId={}, orderNo={}, amount={}, userId={}", orderId, orderNo, orderAmount, orderUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseOrderPerformance(Long orderId, Long refundId, BigDecimal productRefundAmount,
                                        Integer refundQuantity, LocalDateTime refundTime) {
        if (orderId == null || refundId == null || productRefundAmount == null
                || productRefundAmount.compareTo(BigDecimal.ZERO) <= 0
                || refundQuantity == null || refundQuantity <= 0) {
            return;
        }
        LocalDateTime effectiveTime = refundTime == null ? LocalDateTime.now() : refundTime;
        List<DmsOrderPerformanceDetail> allDetails = performanceDetailDao.selectByOrderId(orderId);
        List<DmsOrderPerformanceDetail> originals = allDetails.stream()
                .filter(detail -> detail.getPerformanceAmount() != null && detail.getPerformanceAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(detail -> detail.getRemark() == null || !detail.getRemark().startsWith("退款业绩冲销:"))
                .toList();
        for (DmsOrderPerformanceDetail original : originals) {
            BigDecimal alreadyReversedAmount = allDetails.stream()
                    .filter(item -> Objects.equals(item.getTargetAgentId(), original.getTargetAgentId()))
                    .filter(item -> Objects.equals(item.getRelationLevel(), original.getRelationLevel()))
                    .filter(item -> item.getPerformanceAmount() != null && item.getPerformanceAmount().compareTo(BigDecimal.ZERO) < 0)
                    .map(item -> item.getPerformanceAmount().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainingAmount = original.getPerformanceAmount().subtract(alreadyReversedAmount).max(BigDecimal.ZERO);
            BigDecimal reversalAmount = productRefundAmount.min(remainingAmount).negate();
            DmsOrderPerformanceDetail reversal = new DmsOrderPerformanceDetail();
            BeanUtils.copyProperties(original, reversal, "id", "createTime", "updateTime");
            reversal.setId(null);
            reversal.setOrderTime(effectiveTime);
            int originalUnits = original.getQuantity() == null ? 0 : Math.max(0, original.getQuantity());
            int alreadyReversedUnits = allDetails.stream()
                    .filter(item -> Objects.equals(item.getTargetAgentId(), original.getTargetAgentId()))
                    .filter(item -> Objects.equals(item.getRelationLevel(), original.getRelationLevel()))
                    .filter(item -> item.getQuantity() != null && item.getQuantity() < 0)
                    .mapToInt(item -> -item.getQuantity())
                    .sum();
            int reversalUnits = Math.max(0, Math.min(refundQuantity, originalUnits - alreadyReversedUnits));
            if (reversalAmount.compareTo(BigDecimal.ZERO) == 0 && reversalUnits == 0) continue;
            reversal.setQuantity(-reversalUnits);
            reversal.setProductAmount(reversalAmount);
            reversal.setPerformanceAmount(reversalAmount);
            reversal.setStatus(1);
            reversal.setRemark("退款业绩冲销: refundId=" + refundId + "; 原业绩明细=" + original.getId());
            performanceDetailDao.insert(reversal);
            if (reversalUnits > 0) {
                agentAccountDao.addTotalOrders(original.getTargetAgentId(), -reversalUnits);
            }

            if (original.getRelationLevel() != null && original.getRelationLevel() > 0) {
                updateSubordinateContributionBySnapshot(original, reversalAmount, -reversalUnits,
                        effectiveTime.toLocalDate());
            }
        }
        refreshDailySummary(effectiveTime.toLocalDate());
        refreshMonthlySummary(effectiveTime.toLocalDate());
        log.info("退款业绩冲销完成: orderId={}, refundId={}, productRefundAmount={}, refundQuantity={}",
                orderId, refundId, productRefundAmount, refundQuantity);
    }

    @Override
    public PerformanceOverviewVO getPerformanceOverview(Long agentId, LocalDate startDate, LocalDate endDate) {
        PerformanceOverviewVO vo = new PerformanceOverviewVO();

        // 查询代理信息
        DmsAgent agent = agentDao.selectById(agentId);
        if (agent == null) {
            return vo;
        }

        vo.setAgentId(agentId);
        vo.setAgentName(agent.getAgentName());

        // 查询区间必须按请求的日期实时计算，不能直接复用整月汇总，否则自定义日期会显示错误口径。
        LocalDate effectiveEndDate = endDate == null ? LocalDate.now() : endDate;
        calculatePerformanceOverview(vo, agentId, startDate, effectiveEndDate);

        PerformanceOverviewVO total = new PerformanceOverviewVO();
        calculatePerformanceOverview(total, agentId, null, effectiveEndDate);
        PerformanceOverviewVO currentMonth = new PerformanceOverviewVO();
        calculatePerformanceOverview(currentMonth, agentId,
                effectiveEndDate.withDayOfMonth(1), effectiveEndDate);
        vo.setTotalPersonalPerformance(total.getPersonalPerformance());
        vo.setCurrentMonthPersonalPerformance(currentMonth.getPersonalPerformance());
        vo.setTotalTeamPerformance(total.getTeamPerformance());
        vo.setCurrentMonthTeamPerformance(currentMonth.getTeamPerformance());
        vo.setTotalNewAgentCount((int) countNewSubordinateAgents(agentId, null, effectiveEndDate));
        vo.setCurrentMonthNewAgentCount((int) countNewSubordinateAgents(
                agentId, effectiveEndDate.withDayOfMonth(1), effectiveEndDate));

        return vo;
    }

    @Override
    public PerformanceOverviewVO getProfilePerformanceSummary(Long agentId, LocalDate statDate) {
        LocalDate effectiveDate = statDate == null ? LocalDate.now() : statDate;
        PerformanceOverviewVO summary = performanceDetailDao.selectProfilePerformanceSummary(
                agentId, effectiveDate.withDayOfMonth(1).atStartOfDay(), effectiveDate.plusDays(1).atStartOfDay());
        if (summary == null) {
            summary = new PerformanceOverviewVO();
            summary.setTotalTeamPerformance(BigDecimal.ZERO);
            summary.setCurrentMonthTeamPerformance(BigDecimal.ZERO);
        }
        summary.setAgentId(agentId);
        return summary;
    }

    @Override
    public List<SubordinateContributionVO> getSubordinateContributions(Long agentId, LocalDate startDate, LocalDate endDate) {
        // 查询下属贡献记录
        List<DmsSubordinateContribution> contributions = contributionDao.selectByAgentAndDateRange(
                agentId, startDate, endDate, StatTypeEnum.MONTHLY.getValue());

        // 合并同一下属的贡献
        Map<Long, SubordinateContributionVO> contributionMap = new LinkedHashMap<>();
        for (DmsSubordinateContribution contribution : contributions) {
            SubordinateContributionVO vo = contributionMap.get(contribution.getSubordinateAgentId());
            if (vo == null) {
                vo = new SubordinateContributionVO();
                vo.setSubordinateAgentId(contribution.getSubordinateAgentId());
                vo.setSubordinateName(contribution.getSubordinateName());
                DmsAgent subordinate = agentDao.selectById(contribution.getSubordinateAgentId());
                DmsShopMember subordinateMember = subordinate == null ? null : shopMemberDao.selectByUserId(subordinate.getUserId());
                vo.setSubordinateMemberAccount(MemberAccountUtils.display(subordinateMember));
                vo.setRelationLevel(contribution.getRelationLevel());
                vo.setRelationLevelName(getRelationLevelName(contribution.getRelationLevel()));
                vo.setContributionAmount(BigDecimal.ZERO);
                vo.setOrderCount(0);
                vo.setSelfPerformance(BigDecimal.ZERO);
                vo.setTeamPerformance(BigDecimal.ZERO);
                contributionMap.put(contribution.getSubordinateAgentId(), vo);
            }

            vo.setContributionAmount(vo.getContributionAmount().add(contribution.getContributionAmount()));
            vo.setOrderCount(vo.getOrderCount() + contribution.getOrderCount());
            vo.setSelfPerformance(vo.getSelfPerformance().add(contribution.getSelfPerformance()));
            vo.setTeamPerformance(vo.getTeamPerformance().add(contribution.getTeamPerformance()));
        }

        // 查询下属的下级数量
        for (SubordinateContributionVO vo : contributionMap.values()) {
            List<DmsAgentRelation> children = relationDao.selectDirectChildren(vo.getSubordinateAgentId());
            vo.setSubordinateCount(children.size());
        }

        return new ArrayList<>(contributionMap.values());
    }

    @Override
    public List<OrderPerformanceDetailVO> getSubordinateOrderDetails(Long agentId, Long subordinateAgentId,
                                                                       LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        List<DmsOrderPerformanceDetail> details = performanceDetailDao.selectSubordinateOrderDetails(
                agentId, subordinateAgentId, startTime, endTime);

        return convertToOrderDetailVOList(details);
    }

    @Override
    public List<OrderPerformanceDetailVO> getPerformanceSourceDetails(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<DmsOrderPerformanceDetail> details = new ArrayList<>();
        details.addAll(performanceDetailDao.selectPersonalPerformanceDetails(agentId, startTime, endTime));
        details.addAll(performanceDetailDao.selectTeamPerformanceDetails(agentId, startTime, endTime));
        details.sort(Comparator.comparing(DmsOrderPerformanceDetail::getOrderTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return convertToOrderDetailVOList(details);
    }

    @Override
    public List<PerformanceRankingVO> getPerformanceRanking(Integer rankType, Integer rankPeriod, LocalDate statDate) {
        LocalDate effectiveStatDate = statDate == null ? LocalDate.now() : statDate;
        int effectiveRankType = rankType != null && rankType >= 1 && rankType <= 3 ? rankType : 2;
        int effectiveRankPeriod = rankPeriod != null && rankPeriod >= 1 && rankPeriod <= 4 ? rankPeriod : 3;
        LocalDate[] range = resolveRankRange(effectiveRankPeriod, effectiveStatDate);
        List<PerformanceRankingVO> rankingList = agentDao.selectPerformanceRanking(
                effectiveRankType, range[0].atStartOfDay(), effectiveStatDate.withDayOfMonth(1).atStartOfDay(),
                effectiveStatDate.plusDays(1).atStartOfDay());

        for (PerformanceRankingVO vo : rankingList) {
            AgentLevelEnum level = AgentLevelEnum.getByValue(vo.getAgentLevel());
            vo.setAgentLevelName(level != null ? level.getName() : "未知");
            vo.setRankType(effectiveRankType);
            vo.setRankPeriod(effectiveRankPeriod);
            vo.setStatDate(effectiveStatDate);
        }
        return rankingList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshDailySummary(LocalDate statDate) {
        log.info("开始刷新日业绩汇总: statDate={}", statDate);

        // 查询所有代理
        List<DmsAgent> agents = agentDao.selectAll();

        for (DmsAgent agent : agents) {
            try {
                refreshAgentDailySummary(agent, statDate);
            } catch (Exception e) {
                log.error("刷新代理日业绩汇总失败: agentId={}", agent.getId(), e);
            }
        }

        log.info("刷新日业绩汇总完成: statDate={}", statDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshMonthlySummary(LocalDate statDate) {
        LocalDate monthStart = statDate.withDayOfMonth(1);
        LocalDate monthEnd = statDate.withDayOfMonth(statDate.lengthOfMonth());

        log.info("开始刷新月业绩汇总: month={} - {}", monthStart, monthEnd);

        // 查询所有代理
        List<DmsAgent> agents = agentDao.selectAll();

        for (DmsAgent agent : agents) {
            try {
                refreshAgentMonthlySummary(agent, monthStart, monthEnd);
            } catch (Exception e) {
                log.error("刷新代理月业绩汇总失败: agentId={}", agent.getId(), e);
            }
        }

        log.info("刷新月业绩汇总完成: month={} - {}", monthStart, monthEnd);
    }

    /**
     * 刷新代理日业绩汇总
     */
    private void refreshAgentDailySummary(DmsAgent agent, LocalDate statDate) {
        LocalDateTime startTime = statDate.atStartOfDay();
        LocalDateTime endTime = statDate.atTime(LocalTime.MAX);

        // 查询个人业绩
        List<DmsOrderPerformanceDetail> personalDetails = performanceDetailDao.selectPersonalPerformanceDetails(
                agent.getId(), startTime, endTime);
        BigDecimal personalPerformance = personalDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 查询团队业绩
        List<DmsOrderPerformanceDetail> teamDetails = performanceDetailDao.selectTeamPerformanceDetails(
                agent.getId(), startTime, endTime);
        BigDecimal teamPerformance = teamDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按层级统计
        BigDecimal level1Performance = BigDecimal.ZERO;
        BigDecimal level2Performance = BigDecimal.ZERO;
        BigDecimal level3Performance = BigDecimal.ZERO;
        Set<Long> level1Users = new HashSet<>();
        Set<Long> level2Users = new HashSet<>();
        Set<Long> level3Users = new HashSet<>();

        for (DmsOrderPerformanceDetail detail : teamDetails) {
            switch (detail.getRelationLevel()) {
                case 1:
                    level1Performance = level1Performance.add(detail.getPerformanceAmount());
                    level1Users.add(detail.getOwnerUserId());
                    break;
                case 2:
                    level2Performance = level2Performance.add(detail.getPerformanceAmount());
                    level2Users.add(detail.getOwnerUserId());
                    break;
                case 3:
                    level3Performance = level3Performance.add(detail.getPerformanceAmount());
                    level3Users.add(detail.getOwnerUserId());
                    break;
                default:
                    break;
            }
        }

        // 查询团队人数
        int[] levelCounts = relationService.getLevelMemberCounts(agent.getId());

        // 创建或更新汇总
        DmsAgentPerformanceSummary summary = summaryDao.selectByAgentAndDate(
                agent.getId(), statDate, StatTypeEnum.DAILY.getValue());
        if (summary == null) {
            summary = new DmsAgentPerformanceSummary();
            summary.setAgentId(agent.getId());
            summary.setUserId(agent.getUserId());
            summary.setAgentName(agent.getAgentName());
            summary.setStatDate(statDate);
            summary.setStatType(StatTypeEnum.DAILY.getValue());
        }

        summary.setPersonalOrderCount(sumUnits(personalDetails));
        summary.setPersonalPerformance(personalPerformance);
        summary.setTeamOrderCount(sumUnits(personalDetails) + sumUnits(teamDetails));
        summary.setTeamPerformance(personalPerformance.add(teamPerformance));
        summary.setLevel1Performance(level1Performance);
        summary.setLevel2Performance(level2Performance);
        summary.setLevel3Performance(level3Performance);
        summary.setTeamMemberCount(relationService.getTeamMemberCount(agent.getId()));
        summary.setLevel1MemberCount(levelCounts[0]);
        summary.setLevel2MemberCount(levelCounts[1]);
        summary.setLevel3MemberCount(levelCounts[2]);
        summary.setActiveMemberCount((int) teamDetails.stream().map(DmsOrderPerformanceDetail::getOwnerUserId)
                .filter(Objects::nonNull).distinct().count());

        if (summary.getId() == null) {
            summaryDao.insert(summary);
        } else {
            summaryDao.update(summary);
        }
    }

    /**
     * 刷新代理月业绩汇总（查询整月数据）
     */
    private void refreshAgentMonthlySummary(DmsAgent agent, LocalDate monthStart, LocalDate monthEnd) {
        LocalDateTime startTime = monthStart.atStartOfDay();
        LocalDateTime endTime = monthEnd.atTime(LocalTime.MAX);

        // 查询整月个人业绩
        List<DmsOrderPerformanceDetail> personalDetails = performanceDetailDao.selectPersonalPerformanceDetails(
                agent.getId(), startTime, endTime);
        BigDecimal personalPerformance = personalDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 查询整月团队业绩
        List<DmsOrderPerformanceDetail> teamDetails = performanceDetailDao.selectTeamPerformanceDetails(
                agent.getId(), startTime, endTime);
        BigDecimal teamPerformance = teamDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按层级统计
        BigDecimal level1Performance = BigDecimal.ZERO;
        BigDecimal level2Performance = BigDecimal.ZERO;
        BigDecimal level3Performance = BigDecimal.ZERO;
        Set<Long> level1Users = new HashSet<>();
        Set<Long> level2Users = new HashSet<>();
        Set<Long> level3Users = new HashSet<>();

        for (DmsOrderPerformanceDetail detail : teamDetails) {
            switch (detail.getRelationLevel()) {
                case 1:
                    level1Performance = level1Performance.add(detail.getPerformanceAmount());
                    level1Users.add(detail.getOwnerUserId());
                    break;
                case 2:
                    level2Performance = level2Performance.add(detail.getPerformanceAmount());
                    level2Users.add(detail.getOwnerUserId());
                    break;
                case 3:
                    level3Performance = level3Performance.add(detail.getPerformanceAmount());
                    level3Users.add(detail.getOwnerUserId());
                    break;
                default:
                    break;
            }
        }

        // 查询团队人数
        int[] levelCounts = relationService.getLevelMemberCounts(agent.getId());

        // 创建或更新月度汇总
        DmsAgentPerformanceSummary summary = summaryDao.selectByAgentAndDate(
                agent.getId(), monthEnd, StatTypeEnum.MONTHLY.getValue());
        if (summary == null) {
            summary = new DmsAgentPerformanceSummary();
            summary.setAgentId(agent.getId());
            summary.setUserId(agent.getUserId());
            summary.setAgentName(agent.getAgentName());
            summary.setStatDate(monthEnd);
            summary.setStatType(StatTypeEnum.MONTHLY.getValue());
        }

        summary.setPersonalOrderCount(sumUnits(personalDetails));
        summary.setPersonalPerformance(personalPerformance);
        summary.setTeamOrderCount(sumUnits(personalDetails) + sumUnits(teamDetails));
        summary.setTeamPerformance(personalPerformance.add(teamPerformance));
        summary.setLevel1Performance(level1Performance);
        summary.setLevel2Performance(level2Performance);
        summary.setLevel3Performance(level3Performance);
        summary.setTeamMemberCount(relationService.getTeamMemberCount(agent.getId()));
        summary.setLevel1MemberCount(levelCounts[0]);
        summary.setLevel2MemberCount(levelCounts[1]);
        summary.setLevel3MemberCount(levelCounts[2]);
        summary.setActiveMemberCount((int) teamDetails.stream().map(DmsOrderPerformanceDetail::getOwnerUserId)
                .filter(Objects::nonNull).distinct().count());

        if (summary.getId() == null) {
            summaryDao.insert(summary);
        } else {
            summaryDao.update(summary);
        }
    }

    /**
     * 计算业绩概览
     */
    private void calculatePerformanceOverview(PerformanceOverviewVO vo, Long agentId,
                                               LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        // 查询个人业绩
        List<DmsOrderPerformanceDetail> personalDetails = performanceDetailDao.selectPersonalPerformanceDetails(
                agentId, startTime, endTime);
        vo.setPersonalOrderCount(sumUnits(personalDetails));
        vo.setPersonalPerformance(personalDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 查询团队业绩
        List<DmsOrderPerformanceDetail> teamDetails = performanceDetailDao.selectTeamPerformanceDetails(
                agentId, startTime, endTime);
        vo.setTeamOrderCount(sumUnits(personalDetails) + sumUnits(teamDetails));
        vo.setTeamPerformance(vo.getPersonalPerformance().add(teamDetails.stream()
                .map(DmsOrderPerformanceDetail::getPerformanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));

        // 按层级统计
        BigDecimal level1 = BigDecimal.ZERO;
        BigDecimal level2 = BigDecimal.ZERO;
        BigDecimal level3 = BigDecimal.ZERO;
        Set<Long> activeUsers = new HashSet<>();

        for (DmsOrderPerformanceDetail detail : teamDetails) {
            activeUsers.add(detail.getOwnerUserId());
            switch (detail.getRelationLevel()) {
                case 1:
                    level1 = level1.add(detail.getPerformanceAmount());
                    break;
                case 2:
                    level2 = level2.add(detail.getPerformanceAmount());
                    break;
                case 3:
                    level3 = level3.add(detail.getPerformanceAmount());
                    break;
                default:
                    break;
            }
        }

        vo.setLevel1Performance(level1);
        vo.setLevel2Performance(level2);
        vo.setLevel3Performance(level3);
        vo.setActiveMemberCount(activeUsers.size());

        // 查询团队人数
        int[] levelCounts = relationService.getLevelMemberCounts(agentId);
        vo.setTeamMemberCount(relationService.getTeamMemberCount(agentId));
    }

    private int sumUnits(List<DmsOrderPerformanceDetail> details) {
        return details.stream().map(DmsOrderPerformanceDetail::getQuantity)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private BigDecimal resolveRankingValue(Long agentId, Integer rankType, LocalDate startDate, LocalDate endDate) {
        if (Objects.equals(rankType, 3)) {
            return BigDecimal.valueOf(countNewSubordinateAgents(agentId, startDate, endDate));
        }

        PerformanceOverviewVO overview = new PerformanceOverviewVO();
        calculatePerformanceOverview(overview, agentId, startDate, endDate);
        if (Objects.equals(rankType, 1)) {
            return overview.getPersonalPerformance() != null ? overview.getPersonalPerformance() : BigDecimal.ZERO;
        }
        return overview.getTeamPerformance() != null ? overview.getTeamPerformance() : BigDecimal.ZERO;
    }

    private long countNewSubordinateAgents(Long agentId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        return relationDao.selectAllDescendants(agentId).stream()
                .map(relation -> agentDao.selectById(relation.getAgentId()))
                .filter(Objects::nonNull)
                .filter(agent -> agent.getCreateTime() != null)
                .filter(agent -> (startTime == null || !agent.getCreateTime().isBefore(startTime))
                        && (endTime == null || agent.getCreateTime().isBefore(endTime)))
                .count();
    }

    private LocalDate[] resolveRankRange(Integer rankPeriod, LocalDate statDate) {
        LocalDate date = statDate != null ? statDate : LocalDate.now();
        if (Objects.equals(rankPeriod, 1)) {
            return new LocalDate[]{date, date};
        }
        if (Objects.equals(rankPeriod, 2)) {
            return new LocalDate[]{date.minusDays(6), date};
        }
        if (Objects.equals(rankPeriod, 4)) {
            return new LocalDate[]{date.withDayOfYear(1), date};
        }
        return new LocalDate[]{date.withDayOfMonth(1), date};
    }

    /**
     * 更新下属贡献记录
     */
    private void updateSubordinateContribution(Long agentId, Long subordinateAgentId,
                                                 BigDecimal amount, int quantity, int relationLevel,
                                                 LocalDate statDate) {
        DmsAgent subordinateAgent = agentDao.selectById(subordinateAgentId);
        if (subordinateAgent == null) {
            return;
        }

        // 查询或创建贡献记录
        DmsSubordinateContribution contribution = contributionDao.selectByAgentAndSubordinate(
                agentId, subordinateAgentId, statDate, StatTypeEnum.MONTHLY.getValue());

        if (contribution == null) {
            contribution = new DmsSubordinateContribution();
            contribution.setAgentId(agentId);
            contribution.setSubordinateAgentId(subordinateAgentId);
            contribution.setSubordinateUserId(subordinateAgent.getUserId());
            contribution.setSubordinateName(subordinateAgent.getAgentName());
            contribution.setRelationLevel(relationLevel);
            contribution.setStatDate(statDate);
            contribution.setStatType(StatTypeEnum.MONTHLY.getValue());
            contribution.setContributionAmount(amount);
            contribution.setOrderCount(quantity);
            contribution.setSelfPerformance(amount);
            contribution.setTeamPerformance(BigDecimal.ZERO);
            contributionDao.insert(contribution);
        } else {
            contribution.setContributionAmount(contribution.getContributionAmount().add(amount));
            contribution.setOrderCount(contribution.getOrderCount() + quantity);
            contribution.setSelfPerformance(contribution.getSelfPerformance().add(amount));
            contributionDao.update(contribution);
        }
    }

    /**
     * 退款冲销必须沿用原订单已冻结的层级，而不是读取当前组织关系（移线后尤其重要）。
     */
    private void updateSubordinateContributionBySnapshot(DmsOrderPerformanceDetail original,
                                                          BigDecimal amount, int quantity, LocalDate statDate) {
        DmsAgent subordinateAgent = agentDao.selectById(original.getOwnerAgentId());
        if (subordinateAgent == null || original.getTargetAgentId() == null) {
            return;
        }
        DmsSubordinateContribution contribution = contributionDao.selectByAgentAndSubordinate(
                original.getTargetAgentId(), original.getOwnerAgentId(), statDate, StatTypeEnum.MONTHLY.getValue());
        if (contribution == null) {
            contribution = new DmsSubordinateContribution();
            contribution.setAgentId(original.getTargetAgentId());
            contribution.setSubordinateAgentId(original.getOwnerAgentId());
            contribution.setSubordinateUserId(subordinateAgent.getUserId());
            contribution.setSubordinateName(subordinateAgent.getAgentName());
            contribution.setRelationLevel(original.getRelationLevel());
            contribution.setStatDate(statDate);
            contribution.setStatType(StatTypeEnum.MONTHLY.getValue());
            contribution.setContributionAmount(amount);
            contribution.setOrderCount(quantity);
            contribution.setSelfPerformance(amount);
            contribution.setTeamPerformance(BigDecimal.ZERO);
            contributionDao.insert(contribution);
            return;
        }
        contribution.setContributionAmount(nullToZero(contribution.getContributionAmount()).add(amount));
        contribution.setOrderCount((contribution.getOrderCount() == null ? 0 : contribution.getOrderCount()) + quantity);
        contribution.setSelfPerformance(nullToZero(contribution.getSelfPerformance()).add(amount));
        contributionDao.update(contribution);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 获取关系层级名称
     */
    private String getRelationLevelName(int level) {
        switch (level) {
            case 1:
                return "直属";
            case 2:
                return "二级";
            case 3:
                return "三级";
            default:
                return "第" + level + "层";
        }
    }

    /**
     * 转换为订单明细VO列表
     */
    private List<OrderPerformanceDetailVO> convertToOrderDetailVOList(List<DmsOrderPerformanceDetail> details) {
        List<OrderPerformanceDetailVO> voList = new ArrayList<>();
        for (DmsOrderPerformanceDetail detail : details) {
            OrderPerformanceDetailVO vo = new OrderPerformanceDetailVO();
            BeanUtils.copyProperties(detail, vo);
            vo.setRelationLevelName(getRelationLevelName(detail.getRelationLevel()));
            voList.add(vo);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePerformanceOnSwitchLine(Long agentId, Long oldParentAgentId, Long newParentAgentId) {
        log.info("开始更新切线业绩归属: agentId={}, oldParentId={}, newParentId={}", agentId, oldParentAgentId, newParentAgentId);

        // 1. 查询被切线代理的信息
        DmsAgent agent = agentDao.selectById(agentId);
        if (agent == null) {
            log.error("代理不存在: agentId={}", agentId);
            return;
        }

        // 2. 查询新上级代理信息
        DmsAgent newParentAgent = agentDao.selectById(newParentAgentId);
        if (newParentAgent == null) {
            log.error("新上级代理不存在: agentId={}", newParentAgentId);
            return;
        }

        // 3. 更新被切线代理自己的业绩归属
        // 将该代理的团队业绩（target=旧上级）更新为新上级
        performanceDetailDao.updateTargetAgentId(
                agent.getUserId(),
                oldParentAgentId,
                newParentAgentId,
                newParentAgent.getAgentName()
        );

        // 4. 递归更新所有下级的业绩归属
        updateDescendantPerformance(agentId, oldParentAgentId, newParentAgentId, newParentAgent.getAgentName());

        // 5. 删除旧上级和新上级的所有历史业绩汇总（需要重新计算）
        summaryDao.deleteAllByAgentId(oldParentAgentId);
        summaryDao.deleteAllByAgentId(newParentAgentId);

        // 6. 删除旧上级和新上级的所有历史下属贡献记录（需要重新计算）
        contributionDao.deleteAllByAgentId(oldParentAgentId);
        contributionDao.deleteAllByAgentId(newParentAgentId);

        log.info("切线业绩归属更新完成: agentId={}", agentId);
    }

    /**
     * 递归更新下级代理的业绩归属
     */
    private void updateDescendantPerformance(Long agentId, Long oldParentAgentId, Long newParentAgentId, String newParentAgentName) {
        // 查询该代理的所有直属下级
        List<DmsAgentRelation> children = relationDao.selectDirectChildren(agentId);

        for (DmsAgentRelation child : children) {
            DmsAgent childAgent = agentDao.selectById(child.getAgentId());
            if (childAgent == null) {
                continue;
            }

            // 更新该下级的业绩归属
            performanceDetailDao.updateTargetAgentId(
                    childAgent.getUserId(),
                    oldParentAgentId,
                    newParentAgentId,
                    newParentAgentName
            );

            // 递归更新下级的下级
            updateDescendantPerformance(childAgent.getId(), oldParentAgentId, newParentAgentId, newParentAgentName);
        }
    }
}
