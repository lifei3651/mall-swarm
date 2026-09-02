package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.dto.WithdrawQueryDTO;
import com.macro.mall.distribution.dto.WithdrawConfirmPayDTO;
import com.macro.mall.distribution.service.WithdrawService;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WithdrawStatsVO;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class WithdrawController {

    private final WithdrawService withdrawService;
    private final WithdrawalPayoutService withdrawalPayoutService;
    private final PerformanceService performanceService;

    @Operation(summary = "审核提现")
    @PostMapping("/audit")
    public CommonResult<Boolean> auditWithdraw(@Valid @RequestBody WithdrawAuditDTO auditDTO) {
        if (AdminContext.get() != null) {
            auditDTO.setAuditUserId(AdminContext.get().getId());
            auditDTO.setAuditUserName(AdminContext.get().getNickname());
        }
        boolean approved = Integer.valueOf(1).equals(auditDTO.getStatus());
        if (approved) withdrawalPayoutService.requireReady(auditDTO.getId());
        boolean result = withdrawService.auditWithdraw(auditDTO);
        if (!result) return CommonResult.failed("审核失败");
        if (!approved) return CommonResult.success(true, "已驳回，冻结金额已退回会员余额");
        try {
            WithdrawalPayoutVO payout = withdrawalPayoutService.start(auditDTO.getId());
            if (payout != null && "SUCCESS".equals(payout.getState())) {
                return CommonResult.success(true, "审核通过，官方渠道已核验到账");
            }
            if (payout != null && "FAILED".equals(payout.getState())) {
                return CommonResult.success(true, "审核通过，但渠道返回失败，请在提现记录中重试");
            }
            return CommonResult.success(true, "审核通过，系统已发起渠道打款");
        } catch (RuntimeException error) {
            // 审核已经落库，返回准确结果，避免操作人误以为审核失败后重复点击。
            log.error("提现审核通过后自动发起打款失败: withdrawId={}, error={}",
                    auditDTO.getId(), error.getClass().getSimpleName());
            return CommonResult.success(true, "审核已通过，渠道发起异常，请在提现记录中重试");
        }
    }

    @Deprecated
    @Operation(summary = "旧人工确认打款入口（已安全停用）")
    @PostMapping("/confirm-pay/{id}")
    public CommonResult<Boolean> confirmPay(@PathVariable Long id,
                                            @Valid @RequestBody WithdrawConfirmPayDTO dto) {
        boolean result = withdrawService.confirmPay(id, dto.getPayNo().trim());
        if (result) {
            return CommonResult.success(true);
        }
        return CommonResult.failed("确认打款失败");
    }

    @Operation(summary = "发起微信或支付宝官方渠道打款")
    @PostMapping("/{id}/payout/start")
    public CommonResult<WithdrawalPayoutVO> startPayout(@PathVariable Long id) {
        return CommonResult.success(withdrawalPayoutService.start(id));
    }

    @Operation(summary = "向官方渠道核对提现打款结果")
    @PostMapping("/{id}/payout/reconcile")
    public CommonResult<WithdrawalPayoutVO> reconcilePayout(@PathVariable Long id) {
        return CommonResult.success(withdrawalPayoutService.reconcile(id));
    }

    @Operation(summary = "查询提现渠道打款状态")
    @GetMapping("/{id}/payout")
    public CommonResult<WithdrawalPayoutVO> payout(@PathVariable Long id) {
        WithdrawalPayoutVO payout = withdrawalPayoutService.get(id);
        return payout == null ? CommonResult.failed("尚未发起渠道打款") : CommonResult.success(payout);
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
