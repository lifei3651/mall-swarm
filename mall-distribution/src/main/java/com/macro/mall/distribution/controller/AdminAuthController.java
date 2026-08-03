package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.vo.AdminAuthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AdminAuthController", description = "后台管理员认证")
@RestController
@RequestMapping("/distribution/admin-auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "后台登录")
    @PostMapping("/login")
    public CommonResult<AdminAuthVO> login(@RequestBody AdminLoginDTO dto) {
        return CommonResult.success(adminAuthService.login(dto));
    }

    @Operation(summary = "当前后台账号")
    @GetMapping("/me")
    public CommonResult<AdminAuthVO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(adminAuthService.me(authorization));
    }

    @Operation(summary = "后台退出")
    @PostMapping("/logout")
    public CommonResult<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(adminAuthService.logout(authorization));
    }
}
