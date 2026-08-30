package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.WeChatMiniProgramLoginDTO;
import com.macro.mall.distribution.service.WeChatMiniProgramAuthService;
import com.macro.mall.distribution.vo.WeChatMiniProgramLoginVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramRuntimeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WeChatMiniProgramController", description = "微信小程序通用接入基座")
@RestController
@RequestMapping("/shop/wechat-mini-program")
@RequiredArgsConstructor
public class WeChatMiniProgramController {

    private final WeChatMiniProgramAuthService authService;

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
}
