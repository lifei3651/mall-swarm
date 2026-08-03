package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.vo.LoginCaptchaVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class LoginCaptchaController {
    private final LoginCaptchaService loginCaptchaService;

    @Operation(summary = "获取登录图形验证码")
    @GetMapping
    public CommonResult<LoginCaptchaVO> create(@RequestParam String scene) {
        return CommonResult.success(loginCaptchaService.create(scene));
    }
}
