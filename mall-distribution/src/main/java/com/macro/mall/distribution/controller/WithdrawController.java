package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.dto.WithdrawQueryDTO;
import com.macro.mall.distribution.service.WithdrawService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawStatsVO;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 提现管理控制器
 */
@Tag(name = "WithdrawController", description = "提现管理")
@RestController
@RequestMapping("/distribution/withdraw")
@RequiredArgsConstructor
public class WithdrawController {

    private final WithdrawService withdrawService;
    private final PerformanceService performanceService;

    @Operation(summary = "审核提现")
    @PostMapping("/audit")
    public CommonResult<Boolean> auditWithdraw(@Valid @RequestBody WithdrawAuditDTO auditDTO) {
        if (AdminContext.get() != null) {
            auditDTO.setAuditUserId(AdminContext.get().getId());
            auditDTO.setAuditUserName(AdminContext.get().getNickname());
        }
        boolean result = withdrawService.auditWithdraw(auditDTO);
        if (result) {
            return CommonResult.success(true);
        }
        return CommonResult.failed("审核失败");
    }

    @Operation(summary = "确认打款")
    @PostMapping("/confirm-pay/{id}")
    public CommonResult<Boolean> confirmPay(@PathVariable Long id, @RequestParam String payNo) {
        boolean result = withdrawService.confirmPay(id, payNo);
        if (result) {
            return CommonResult.success(true);
        }
        return CommonResult.failed("确认打款失败");
    }

    @Operation(summary = "查询提现记录")
    @GetMapping("/{id}")
    public CommonResult<WithdrawRecordVO> getWithdrawById(@PathVariable Long id) {
        WithdrawRecordVO record = withdrawService.getWithdrawById(id);
        if (record == null) {
            return CommonResult.failed("提现记录不存在");
        }
        return CommonResult.success(record);
    }

    @Operation(summary = "查询代理的提现记录")
    @GetMapping("/agent/{agentId}")
    public CommonResult<CommonPage<WithdrawRecordVO>> getWithdrawsByAgentId(@PathVariable Long agentId) {
        List<WithdrawRecordVO> records = withdrawService.getWithdrawsByAgentId(agentId);
        return CommonResult.success(CommonPage.restPage(records));
    }

    @Operation(summary = "按条件查询提现记录")
    @GetMapping("/list")
    public CommonResult<CommonPage<WithdrawRecordVO>> searchWithdraws(
            @RequestParam(required = false) String memberKey,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        WithdrawQueryDTO queryDTO = buildQuery(memberKey, agentId, status, startDate, endDate);
        PageHelper.startPage(pageNum, pageSize);
        List<WithdrawRecordVO> records = withdrawService.searchWithdraws(queryDTO);
        return CommonResult.success(CommonPage.restPage(records));
    }

    @Operation(summary = "查询提现统计")
    @GetMapping("/stats")
    public CommonResult<WithdrawStatsVO> getWithdrawStats(
            @RequestParam(required = false) String memberKey,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        WithdrawQueryDTO queryDTO = buildQuery(memberKey, agentId, status, startDate, endDate);
        return CommonResult.success(withdrawService.getWithdrawStats(queryDTO));
    }

    @Operation(summary = "查询待审核的提现记录")
    @GetMapping("/pending-audit")
    public CommonResult<CommonPage<WithdrawRecordVO>> getPendingAuditWithdraws() {
        List<WithdrawRecordVO> records = withdrawService.getPendingAuditWithdraws();
        return CommonResult.success(CommonPage.restPage(records));
    }

    @Operation(summary = "查询所有提现记录")
    @GetMapping("/all")
    public CommonResult<CommonPage<WithdrawRecordVO>> getAllWithdraws() {
        List<WithdrawRecordVO> records = withdrawService.getAllWithdraws();
        return CommonResult.success(CommonPage.restPage(records));
    }

    private WithdrawQueryDTO buildQuery(String memberKey, Long agentId, Integer status, LocalDate startDate, LocalDate endDate) {
        WithdrawQueryDTO queryDTO = new WithdrawQueryDTO();
        queryDTO.setMemberKey(memberKey);
        queryDTO.setAgentId(memberKey != null && !memberKey.isBlank()
                ? performanceService.resolveAgentId(memberKey) : agentId);
        queryDTO.setStatus(status);
        queryDTO.setStartDate(startDate);
        queryDTO.setEndDate(endDate);
        return queryDTO;
    }
}
