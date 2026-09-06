package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.WeChatMiniProgramBonusService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/wechat-mini-program")
@RequiredArgsConstructor
public class WeChatMiniProgramBonusController {
    private final ShopAuthService authService;
    private final WeChatMiniProgramBonusService bonusService;

    @GetMapping("/bonus-summary")
    public CommonResult<WeChatMiniProgramBonusService.Summary> summary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(bonusService.summary(authService.requireMember(authorization)));
    }
}
