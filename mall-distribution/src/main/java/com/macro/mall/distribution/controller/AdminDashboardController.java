package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.service.AdminDashboardService;
import com.macro.mall.distribution.service.AdminDashboardSpreadsheetService;
import com.macro.mall.distribution.vo.AdminDashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;

@Tag(name = "AdminDashboardController", description = "后台控制台")
@RestController
@RequestMapping("/distribution/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;
    private final AdminDashboardSpreadsheetService spreadsheetService;

    @Operation(summary = "控制台实时统计")
    @GetMapping
    public CommonResult<AdminDashboardVO> dashboard() {
        return CommonResult.success(dashboardService.getDashboard());
    }

    @Operation(summary = "导出控制台经营报表")
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        String filename = "商城经营报表-" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build().toString());
        spreadsheetService.write(dashboardService.getDashboard(), response.getOutputStream());
    }
}
