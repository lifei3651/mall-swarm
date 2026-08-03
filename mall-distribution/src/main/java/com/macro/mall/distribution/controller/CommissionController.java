package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.CommissionQueryDTO;
import com.macro.mall.distribution.dto.CommissionSettlementBatchCreateDTO;
import com.macro.mall.distribution.entity.DmsBonusCalculationTask;
import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import com.macro.mall.distribution.service.BonusCalculationTaskService;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import com.macro.mall.distribution.vo.CommissionRecordVO;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 佣金管理控制器
 */
@Tag(name = "CommissionController", description = "佣金管理")
@RestController
@RequestMapping("/distribution/commission")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;
    private final CommissionSettlementService settlementService;
    private final BonusCalculationTaskService bonusCalculationTaskService;

    @Operation(summary = "计算订单佣金（订单完成后调用）")
    @PostMapping("/calculate")
    public CommonResult<Boolean> calculateCommission(
            @RequestParam Long orderId,
            @RequestParam String orderNo,
            @RequestParam BigDecimal orderAmount,
            @RequestParam Long orderUserId,
            @RequestParam String orderUserName) {
        return CommonResult.failed("订单佣金请通过支付确认、订单导入或奖金任务流程计算");
    }

    @Operation(summary = "提交订单奖金异步计算任务（订单完成后推荐调用）")
    @PostMapping("/calculate-async")
    public CommonResult<DmsBonusCalculationTask> calculateCommissionAsync(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long ruleVersionId,
            @RequestParam Long orderId,
            @RequestParam String orderNo,
            @RequestParam BigDecimal orderAmount,
            @RequestParam Long orderUserId,
            @RequestParam String orderUserName) {
        return CommonResult.failed("奖金计算任务请由订单支付确认或导入流程创建");
    }

    @Operation(summary = "查询奖金异步计算任务")
    @GetMapping("/calculation-tasks")
    public CommonResult<CommonPage<DmsBonusCalculationTask>> listCalculationTasks(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(bonusCalculationTaskService.listTasks(status, orderId)));
    }

    @Operation(summary = "手动处理待计算奖金任务")
    @PostMapping("/calculation-tasks/process")
    public CommonResult<Integer> processCalculationTasks(@RequestParam(defaultValue = "20") Integer limit) {
        return CommonResult.success(bonusCalculationTaskService.processPendingTasks(limit));
    }

    @Operation(summary = "手动处理指定奖金任务")
    @PostMapping("/calculation-tasks/{taskId}/process")
    public CommonResult<Boolean> processCalculationTask(@PathVariable Long taskId) {
        return CommonResult.success(bonusCalculationTaskService.processTask(taskId));
    }

    @Operation(summary = "结算佣金")
    @PostMapping("/settle/{recordId}")
    public CommonResult<Boolean> settleCommission(@PathVariable Long recordId) {
        return CommonResult.failed("奖金在订单确认收货满7天且无待处理售后后自动结算，禁止手工提前结算");
    }

    @Operation(summary = "批量结算佣金")
    @PostMapping("/settle-batch")
    public CommonResult<Integer> settleCommissionBatch(@RequestBody List<Long> recordIds) {
        return CommonResult.failed("奖金在订单确认收货满7天且无待处理售后后自动结算，禁止手工提前结算");
    }

    @Operation(summary = "创建月度佣金结算锁定批次")
    @PostMapping("/settlement-batches")
    public CommonResult<DmsCommissionSettlementBatch> createSettlementBatch(@RequestBody CommissionSettlementBatchCreateDTO dto) {
        return CommonResult.success(settlementService.createBatch(dto));
    }

    @Operation(summary = "查询月度佣金结算批次")
    @GetMapping("/settlement-batches")
    public CommonResult<List<DmsCommissionSettlementBatch>> listSettlementBatches(@RequestParam(required = false) Integer status) {
        return CommonResult.success(settlementService.listBatches(status));
    }

    @Operation(summary = "查询月度佣金结算批次明细")
    @GetMapping("/settlement-batches/{id}/items")
    public CommonResult<List<DmsCommissionSettlementItem>> listSettlementItems(@PathVariable Long id) {
        return CommonResult.success(settlementService.listItems(id));
    }

    @Operation(summary = "执行已锁定的月度佣金结算批次")
    @PostMapping("/settlement-batches/{id}/execute")
    public CommonResult<DmsCommissionSettlementBatch> executeSettlementBatch(@PathVariable Long id) {
        return CommonResult.success(settlementService.executeBatch(id));
    }

    @Operation(summary = "取消佣金")
    @PostMapping("/cancel/{recordId}")
    public CommonResult<Boolean> cancelCommission(
            @PathVariable Long recordId,
            @RequestBody String cancelReason) {
        boolean result = commissionService.cancelCommission(recordId, cancelReason);
        if (result) {
            return CommonResult.success(true);
        }
        return CommonResult.failed("取消失败");
    }

    @Operation(summary = "查询代理的佣金记录")
    @PostMapping("/records")
    public CommonResult<CommonPage<CommissionRecordVO>> getCommissionRecords(@RequestBody CommissionQueryDTO queryDTO) {
        List<CommissionRecordVO> records = commissionService.getCommissionRecords(queryDTO);
        return CommonResult.success(CommonPage.restPage(records));
    }

    @Operation(summary = "查询代理的待结算佣金总额")
    @GetMapping("/unsettled/{agentId}")
    public CommonResult<BigDecimal> getUnsettledAmount(@PathVariable Long agentId) {
        BigDecimal amount = commissionService.getUnsettledAmount(agentId);
        return CommonResult.success(amount);
    }

    @Operation(summary = "查询代理的已结算佣金总额")
    @GetMapping("/settled/{agentId}")
    public CommonResult<BigDecimal> getSettledAmount(@PathVariable Long agentId) {
        BigDecimal amount = commissionService.getSettledAmount(agentId);
        return CommonResult.success(amount);
    }

}
