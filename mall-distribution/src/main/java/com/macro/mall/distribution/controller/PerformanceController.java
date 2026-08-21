package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.config.DistributedScheduledTaskRunner;
import com.macro.mall.distribution.vo.OrderPerformanceDetailVO;
import com.macro.mall.distribution.vo.PerformanceOverviewVO;
import com.macro.mall.distribution.vo.PerformanceRankingVO;
import com.macro.mall.distribution.vo.SubordinateContributionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import com.github.pagehelper.PageHelper;

/**
 * 业绩统计控制器
 */
@Tag(name = "PerformanceController", description = "业绩统计")
@RestController
@RequestMapping("/distribution/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final DistributedScheduledTaskRunner scheduledTaskRunner;

    @Operation(summary = "记录订单业绩")
    @PostMapping("/record")
    public CommonResult<Boolean> recordOrderPerformance(
            @RequestParam Long orderId,
            @RequestParam String orderNo,
            @RequestParam java.math.BigDecimal orderAmount,
            @RequestParam Long orderUserId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") java.time.LocalDateTime orderTime) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "订单业绩请通过支付确认或订单导入流程记录");
    }

    @Operation(summary = "查询代理的业绩概览")
    @GetMapping("/overview/{agentKey}")
    public CommonResult<PerformanceOverviewVO> getPerformanceOverview(
            @PathVariable String agentKey,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Long agentId = performanceService.resolveAgentId(agentKey);
        PerformanceOverviewVO overview = performanceService.getPerformanceOverview(agentId, startDate, endDate);
        return CommonResult.success(overview);
    }

    @Operation(summary = "查询代理的团队成员贡献列表")
    @GetMapping("/contributions/{agentKey}")
    public CommonResult<CommonPage<SubordinateContributionVO>> getSubordinateContributions(
            @PathVariable String agentKey,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Long agentId = performanceService.resolveAgentId(agentKey);
        List<SubordinateContributionVO> contributions = performanceService.getSubordinateContributions(agentId, startDate, endDate);
        return CommonResult.success(CommonPage.restPage(contributions));
    }

    @Operation(summary = "查询某个下属贡献的具体订单明细")
    @GetMapping("/contributions/{agentKey}/details/{subordinateAgentId}")
    public CommonResult<CommonPage<OrderPerformanceDetailVO>> getSubordinateOrderDetails(
            @PathVariable String agentKey,
            @PathVariable Long subordinateAgentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Long agentId = performanceService.resolveAgentId(agentKey);
        List<OrderPerformanceDetailVO> details = performanceService.getSubordinateOrderDetails(
                agentId, subordinateAgentId, startDate, endDate);
        return CommonResult.success(CommonPage.restPage(details));
    }

    @Operation(summary = "查询代理全部业绩来源明细")
    @GetMapping("/sources/{agentKey}")
    public CommonResult<CommonPage<OrderPerformanceDetailVO>> getPerformanceSourceDetails(
            @PathVariable String agentKey,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Long agentId = performanceService.resolveAgentId(agentKey);
        List<OrderPerformanceDetailVO> details = performanceService.getPerformanceSourceDetails(agentId, startDate, endDate);
        return CommonResult.success(CommonPage.restPage(details));
    }

    @Operation(summary = "查询业绩排行榜")
    @GetMapping("/ranking")
    public CommonResult<CommonPage<PerformanceRankingVO>> getPerformanceRanking(
            @RequestParam(defaultValue = "2") Integer rankType,
            @RequestParam(defaultValue = "3") Integer rankPeriod,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        PageHelper.startPage(safePageNum, safePageSize);
        List<PerformanceRankingVO> ranking = performanceService.getPerformanceRanking(rankType, rankPeriod, statDate);
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).setRanking((safePageNum - 1) * safePageSize + i + 1);
        }
        return CommonResult.success(CommonPage.restPage(ranking));
    }

    @Operation(summary = "刷新日业绩汇总")
    @PostMapping("/refresh/daily")
    public CommonResult<Boolean> refreshDailySummary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate) {
        boolean executed = scheduledTaskRunner.run("daily-performance-summary", Duration.ofHours(2),
                () -> performanceService.refreshDailySummary(statDate));
        return executed ? CommonResult.success(true) : CommonResult.failed("业绩日汇总正在执行，请稍后重试");
    }

    @Operation(summary = "刷新月业绩汇总")
    @PostMapping("/refresh/monthly")
    public CommonResult<Boolean> refreshMonthlySummary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate) {
        boolean executed = scheduledTaskRunner.run("monthly-performance-summary", Duration.ofHours(4),
                () -> performanceService.refreshMonthlySummary(statDate));
        return executed ? CommonResult.success(true) : CommonResult.failed("业绩月汇总正在执行，请稍后重试");
    }

}
