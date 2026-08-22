package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.AdminLoginDTO;
import com.macro.mall.distribution.dto.AdminSelfPasswordDTO;
import com.macro.mall.distribution.dto.AdminStepUpDTO;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.AdminUserService;
import com.macro.mall.distribution.service.AdminStepUpService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.security.AdminSessionCookieService;
import com.macro.mall.distribution.vo.AdminAuthVO;
import com.macro.mall.distribution.vo.AdminStepUpTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AdminAuthController", description = "后台管理员认证")
@RestController
@RequestMapping("/distribution/admin-auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminSessionCookieService cookieService;
    private final OperationLogService operationLogService;
    private final AdminUserService adminUserService;
    private final AdminStepUpService adminStepUpService;

    @Operation(summary = "后台登录")
    @PostMapping("/login")
    public CommonResult<AdminAuthVO> login(@Valid @RequestBody AdminLoginDTO dto,
                                           HttpServletRequest request, HttpServletResponse response) {
        AdminAuthVO auth;
        try {
            auth = adminAuthService.login(dto);
        } catch (RuntimeException ex) {
            operationLogService.log("ADMIN_AUTH", "LOGIN_FAILED", "ADMIN_USERNAME", dto.getUsername(),
                    null, null, "后台登录失败（不记录密码和验证码）");
            throw ex;
        }
        if (auth.getAdmin() != null) {
            try {
                AdminContext.set(auth.getAdmin());
                operationLogService.log("ADMIN_AUTH", "LOGIN_SUCCESS", "ADMIN_USER",
                        String.valueOf(auth.getAdmin().getId()), null, "session-created", "后台登录成功");
            } finally {
                AdminContext.clear();
            }
        }
        cookieService.write(request, response, auth.getToken(), auth.getExpireTime());
        if (AdminSessionCookieService.CLIENT_HEADER_VALUE.equals(request.getHeader(AdminSessionCookieService.CLIENT_HEADER))) {
            auth.setToken(null);
        }
        return CommonResult.success(auth);
    }

    @Operation(summary = "当前后台账号")
    @GetMapping("/me")
    public CommonResult<AdminAuthVO> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return CommonResult.success(adminAuthService.me(authorization));
    }

    @Operation(summary = "当前管理员修改自己的密码")
    @PutMapping("/password")
    public CommonResult<Boolean> changeOwnPassword(@Valid @RequestBody AdminSelfPasswordDTO dto) {
        return CommonResult.success(adminUserService.changeOwnPassword(dto));
    }

    @Operation(summary = "高风险后台操作二次验证")
    @PostMapping("/step-up")
    public CommonResult<AdminStepUpTokenVO> stepUp(@Valid @RequestBody AdminStepUpDTO dto) {
        adminAuthService.verifyPassword(AdminContext.get(), dto.getPassword());
        return CommonResult.success(adminStepUpService.issue(AdminContext.get(), dto.getMethod(), dto.getPath()));
    }

    @Operation(summary = "后台退出")
    @PostMapping("/logout")
    public CommonResult<Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        HttpServletRequest request, HttpServletResponse response) {
        boolean loggedOut = adminAuthService.logout(authorization);
        operationLogService.log("ADMIN_AUTH", "LOGOUT", "ADMIN_USER",
                AdminContext.get() == null ? null : String.valueOf(AdminContext.get().getId()),
                "session-active", "session-revoked", "后台主动退出");
        cookieService.clear(request, response);
        return CommonResult.success(loggedOut);
    }
}
