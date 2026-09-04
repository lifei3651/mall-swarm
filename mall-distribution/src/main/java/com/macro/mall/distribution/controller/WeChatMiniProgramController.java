package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.WeChatMiniProgramLoginDTO;
import com.macro.mall.distribution.dto.WeChatSubscriptionGrantDTO;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.WeChatMiniProgramAuthService;
import com.macro.mall.distribution.service.WeChatSubscriptionService;
import com.macro.mall.distribution.vo.WeChatMiniProgramLoginVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramRuntimeVO;
import com.macro.mall.distribution.vo.WeChatSubscriptionTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "WeChatMiniProgramController", description = "微信小程序通用接入基座")
@RestController
@RequestMapping("/shop/wechat-mini-program")
@RequiredArgsConstructor
public class WeChatMiniProgramController {

    private final WeChatMiniProgramAuthService authService;
    private final ShopAuthService shopAuthService;
    private final WeChatSubscriptionService subscriptionService;

    @Operation(summary = "查询小程序能力是否已由当前客户正式配置")
    @GetMapping("/runtime")
    public CommonResult<WeChatMiniProgramRuntimeVO> runtime() {
        return CommonResult.success(authService.runtime());
    }

    @Operation(summary = "微信登录或手机号快捷注册，不返回OpenID和session_key")
    @PostMapping("/auth/login")
    public CommonResult<WeChatMiniProgramLoginVO> login(
            @Valid @RequestBody WeChatMiniProgramLoginDTO dto) {
        return CommonResult.success(authService.login(dto));
    }

    @Operation(summary = "查询当前会员可授权的微信订阅模板及剩余授权次数")
    @GetMapping("/subscriptions")
    public CommonResult<List<WeChatSubscriptionTemplateVO>> subscriptions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(subscriptionService.status(shopAuthService.requireMember(authorization)));
    }

    @Operation(summary = "记录wx.requestSubscribeMessage明确返回accept的单次授权")
    @PostMapping("/subscriptions/grants")
    public CommonResult<List<WeChatSubscriptionTemplateVO>> recordSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WeChatSubscriptionGrantDTO input) {
        return CommonResult.success(subscriptionService.record(
                shopAuthService.requireMember(authorization), input));
    }
}
