package com.macro.mall.distribution.controller;

import com.macro.mall.common.annotation.Idempotent;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.BalancePayDTO;
import com.macro.mall.distribution.dto.BalanceTransferDTO;
import com.macro.mall.distribution.dto.BalanceRecipientQueryDTO;
import com.macro.mall.distribution.dto.PaymentPasswordDTO;
import com.macro.mall.distribution.dto.ShopWithdrawalApplyDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopWalletService;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.vo.BalanceRecipientVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.util.ShopPublicViewSanitizer;
import com.macro.mall.distribution.vo.ShopWalletSummaryVO;
import com.macro.mall.distribution.vo.WithdrawRecordVO;
import com.macro.mall.distribution.vo.WechatTransferConfirmationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

@Tag(name = "ShopWalletController", description = "商城余额与支付密码")
@RestController
@RequestMapping("/shop/wallet")
@RequiredArgsConstructor
@Slf4j
public class ShopWalletController {

    private final ShopAuthService authService;
    private final ShopWalletService walletService;
    private final WithdrawalPayoutService withdrawalPayoutService;

    @Operation(summary = "余额与支付密码状态")
    @GetMapping("/summary")
    public CommonResult<ShopWalletSummaryVO> summary(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(walletService.getSummary(authService.requireMember(authorization)));
    }

    @Operation(summary = "按手机号确认收款会员")
    @PostMapping("/recipient")
    public CommonResult<BalanceRecipientVO> recipient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BalanceRecipientQueryDTO dto) {
        authService.requireSurface(authorization, "integrated");
        return CommonResult.success(walletService.findRecipient(authService.requireMember(authorization),
                dto == null ? null : dto.getPhone()));
    }

    @Operation(summary = "设置或修改独立支付密码")
    @PutMapping("/payment-password")
    public CommonResult<Boolean> setPaymentPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PaymentPasswordDTO dto) {
        return CommonResult.success(walletService.setPaymentPassword(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "按会员手机号转账余额")
    @PostMapping("/transfers")
    @Idempotent(timeout = 30, message = "转账正在处理，请勿重复操作")
    public CommonResult<Boolean> transfer(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BalanceTransferDTO dto) {
        authService.requireSurface(authorization, "integrated");
        return CommonResult.success(walletService.transfer(authService.requireMember(authorization), dto));
    }

    @Operation(summary = "使用余额支付订单")
    @PostMapping("/orders/{orderId}/pay")
    @Idempotent(timeout = 30, message = "支付正在处理，请勿重复操作")
    public CommonResult<ShopOrderVO> payOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long orderId,
            @Valid @RequestBody BalancePayDTO dto) {
        return CommonResult.success(ShopPublicViewSanitizer.order(
                walletService.payOrder(authService.requireMember(authorization), orderId, dto)));
    }

    @Operation(summary = "会员申请余额提现")
    @PostMapping("/withdrawals")
    @Idempotent(timeout = 30, message = "提现申请正在处理，请勿重复操作")
    public CommonResult<WithdrawRecordVO> applyWithdrawal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ShopWithdrawalApplyDTO dto) {
        DmsShopMember member = authService.requireMember(authorization);
        WithdrawRecordVO record = walletService.applyWithdrawal(member, dto);
        if (Integer.valueOf(1).equals(record.getStatus())) {
            Long withdrawId = record.getId();
            try {
                withdrawalPayoutService.start(withdrawId);
                record = walletService.listWithdrawals(member).stream()
                        .filter(item -> withdrawId.equals(item.getId()))
                        .findFirst().orElse(record);
            } catch (RuntimeException error) {
                // 申请和余额扣减已经提交，渠道异常不能伪装成“申请失败”诱导会员重复提交。
                log.error("自动发起提现渠道打款失败: withdrawId={}, error={}",
                        withdrawId, error.getClass().getSimpleName());
            }
        }
        return CommonResult.success(record);
    }

    @Operation(summary = "会员查询自己的提现记录")
    @GetMapping("/withdrawals")
    public CommonResult<List<WithdrawRecordVO>> listWithdrawals(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(walletService.listWithdrawals(authService.requireMember(authorization)));
    }

    @Operation(summary = "微信小程序为本人核对并拉取待确认收款参数")
    @PostMapping("/withdrawals/{id}/wechat-confirmation")
    public CommonResult<WechatTransferConfirmationVO> prepareWechatConfirmation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        DmsShopMember member = authService.requireMember(authorization);
        return CommonResult.success(withdrawalPayoutService.prepareWechatConfirmation(member, id));
    }

    @Operation(summary = "会员查询自己的余额流水")
    @GetMapping("/flows")
    public CommonResult<List<DmsMemberAssetFlow>> listBalanceFlows(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(walletService.listBalanceFlows(authService.requireMember(authorization)));
    }
}
