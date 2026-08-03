package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.AgentAccountService;
import com.macro.mall.distribution.vo.AgentAccountVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 代理账户控制器
 */
@Tag(name = "AccountController", description = "代理账户")
@RestController
@RequestMapping("/distribution/account")
@RequiredArgsConstructor
public class AccountController {

    private final AgentAccountService accountService;

    @Operation(summary = "查询代理账户信息（按代理ID）")
    @GetMapping("/agent/{agentId}")
    public CommonResult<AgentAccountVO> getAccountByAgentId(@PathVariable Long agentId) {
        AgentAccountVO account = accountService.getAccountByAgentId(agentId);
        if (account == null) {
            return CommonResult.failed("账户不存在");
        }
        return CommonResult.success(account);
    }

    @Operation(summary = "查询代理账户信息（按用户ID）")
    @GetMapping("/user/{userId}")
    public CommonResult<AgentAccountVO> getAccountByUserId(@PathVariable Long userId) {
        AgentAccountVO account = accountService.getAccountByUserId(userId);
        if (account == null) {
            return CommonResult.failed("账户不存在");
        }
        return CommonResult.success(account);
    }

    @Operation(summary = "增加佣金（内部接口）")
    @PostMapping("/commission/add")
    public CommonResult<Boolean> addCommission(
            @RequestParam Long agentId,
            @RequestParam BigDecimal amount) {
        return CommonResult.failed("佣金请通过真实订单或审核流程入账");
    }

    @Operation(summary = "结算佣金（内部接口）")
    @PostMapping("/commission/settle")
    public CommonResult<Boolean> settleCommission(
            @RequestParam Long agentId,
            @RequestParam BigDecimal amount) {
        return CommonResult.failed("佣金结算请使用佣金记录结算接口");
    }

    @Operation(summary = "增加可提现余额（内部接口）")
    @PostMapping("/balance/add")
    public CommonResult<Boolean> addAvailableBalance(
            @RequestParam Long agentId,
            @RequestParam BigDecimal amount) {
        return CommonResult.failed("可提现余额请通过佣金结算或退款审核流程变更");
    }

}
