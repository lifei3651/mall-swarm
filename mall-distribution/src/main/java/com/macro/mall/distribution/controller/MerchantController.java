package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.MerchantDepositAdjustDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalApplyDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalPayDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalRejectDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalReviewDTO;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantAccount;
import com.macro.mall.distribution.entity.DmsMerchantDepositFlow;
import com.macro.mall.distribution.entity.DmsMerchantSettlement;
import com.macro.mall.distribution.entity.DmsMerchantWithdrawal;
import com.macro.mall.distribution.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "MerchantController", description = "可选多商户与货款结算")
@RestController
@RequestMapping("/distribution")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;

    @Operation(summary = "商户列表")
    @GetMapping("/merchants")
    public CommonResult<List<DmsMerchant>> merchants(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer status) {
        return CommonResult.success(merchantService.listMerchants(keyword, status));
    }

    @Operation(summary = "新增商户")
    @PostMapping("/merchants")
    public CommonResult<DmsMerchant> save(@Valid @RequestBody DmsMerchant merchant) {
        return CommonResult.success(merchantService.saveMerchant(merchant));
    }

    @Operation(summary = "修改商户")
    @PutMapping("/merchants/{id}")
    public CommonResult<DmsMerchant> update(@PathVariable Long id, @Valid @RequestBody DmsMerchant merchant) {
        return CommonResult.success(merchantService.updateMerchant(id, merchant));
    }

    @Operation(summary = "启用或停用商户")
    @PutMapping("/merchants/{id}/status")
    public CommonResult<Boolean> status(@PathVariable Long id, @RequestParam Integer status) {
        return CommonResult.success(merchantService.updateMerchantStatus(id, status));
    }

    @Operation(summary = "商户货款账户")
    @GetMapping("/merchant-finance/accounts")
    public CommonResult<List<DmsMerchantAccount>> accounts(@RequestParam(required = false) String keyword) {
        return CommonResult.success(merchantService.listAccounts(keyword));
    }

    @Operation(summary = "商户货款明细")
    @GetMapping("/merchant-finance/settlements")
    public CommonResult<List<DmsMerchantSettlement>> settlements(@RequestParam(required = false) Long merchantId,
                                                                  @RequestParam(required = false) String status) {
        return CommonResult.success(merchantService.listSettlements(merchantId, status));
    }

    @Operation(summary = "商户提现列表")
    @GetMapping("/merchant-finance/withdrawals")
    public CommonResult<List<DmsMerchantWithdrawal>> withdrawals(@RequestParam(required = false) Long merchantId,
                                                                  @RequestParam(required = false) String status) {
        return CommonResult.success(merchantService.listWithdrawals(merchantId, status));
    }

    @Operation(summary = "商户保证金流水")
    @GetMapping("/merchant-finance/deposit-flows")
    public CommonResult<List<DmsMerchantDepositFlow>> depositFlows(@RequestParam(required = false) Long merchantId) {
        return CommonResult.success(merchantService.listDepositFlows(merchantId));
    }

    @Operation(summary = "从商户可提现余额冻结保证金")
    @PostMapping("/merchant-finance/deposits/freeze")
    public CommonResult<DmsMerchantDepositFlow> freezeDeposit(@Valid @RequestBody MerchantDepositAdjustDTO dto) {
        return CommonResult.success(merchantService.freezeDeposit(dto));
    }

    @Operation(summary = "解冻商户保证金")
    @PostMapping("/merchant-finance/deposits/release")
    public CommonResult<DmsMerchantDepositFlow> releaseDeposit(@Valid @RequestBody MerchantDepositAdjustDTO dto) {
        return CommonResult.success(merchantService.releaseDeposit(dto));
    }

    @Operation(summary = "代商户提交提现申请")
    @PostMapping("/merchant-finance/withdrawals")
    public CommonResult<DmsMerchantWithdrawal> apply(@Valid @RequestBody MerchantWithdrawalApplyDTO dto) {
        return CommonResult.success(merchantService.applyWithdrawal(dto));
    }

    @Operation(summary = "登记发票及打款调整")
    @PutMapping("/merchant-finance/withdrawals/{id}/review")
    public CommonResult<DmsMerchantWithdrawal> review(@PathVariable Long id,
                                                       @Valid @RequestBody MerchantWithdrawalReviewDTO dto) {
        return CommonResult.success(merchantService.reviewWithdrawal(id, dto));
    }

    @Operation(summary = "确认商户打款")
    @PostMapping("/merchant-finance/withdrawals/{id}/pay")
    public CommonResult<DmsMerchantWithdrawal> pay(@PathVariable Long id,
                                                    @Valid @RequestBody MerchantWithdrawalPayDTO dto) {
        return CommonResult.success(merchantService.confirmPayment(id, dto));
    }

    @Operation(summary = "驳回商户提现")
    @PostMapping("/merchant-finance/withdrawals/{id}/reject")
    public CommonResult<DmsMerchantWithdrawal> reject(@PathVariable Long id,
                                                       @Valid @RequestBody MerchantWithdrawalRejectDTO dto) {
        return CommonResult.success(merchantService.rejectWithdrawal(id, dto));
    }
}
