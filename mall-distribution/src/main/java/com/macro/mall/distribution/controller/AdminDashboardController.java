package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.AdminDashboardService;
import com.macro.mall.distribution.vo.AdminDashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminDashboardController", description = "后台控制台")
@RestController
@RequestMapping("/distribution/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @Operation(summary = "控制台实时统计")
    @GetMapping
    public CommonResult<AdminDashboardVO> dashboard() {
        return CommonResult.success(dashboardService.getDashboard());
    }
}
