package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.distribution.dto.OrderCompanyShareDTO;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.dto.OrderFinanceDTO;
import com.macro.mall.distribution.dto.PerformanceViewPermissionDTO;
import com.macro.mall.distribution.dto.PerformanceVisibilityDTO;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsFinanceRiskRule;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.vo.*;
import com.github.pagehelper.PageHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "DistributionAuditController", description = "分销配置与账务审计")
@RestController
@RequestMapping("/distribution/audit")
@RequiredArgsConstructor
public class DistributionAuditController {

    private final DistributionAuditService auditService;

    @Operation(summary = "查询分销配置")
    @GetMapping("/settings")
    public CommonResult<DistributionSettingsVO> getSettings() {
        return CommonResult.success(auditService.getSettings());
    }

    @Operation(summary = "更新团队业绩可见性总开关")
    @PutMapping("/settings/visibility")
    public CommonResult<DistributionSettingsVO> updateVisibility(@Valid @RequestBody PerformanceVisibilityDTO dto) {
        return CommonResult.success(auditService.updateVisibility(dto));
    }

    @Operation(summary = "保存单账号业绩查看权限")
    @PostMapping("/settings/permissions")
    public CommonResult<PerformanceViewPermissionVO> savePermission(@Valid @RequestBody PerformanceViewPermissionDTO dto) {
        return CommonResult.success(auditService.savePermission(dto));
    }

    @Operation(summary = "删除单账号业绩查看权限")
    @DeleteMapping("/settings/permissions/{id}")
    public CommonResult<Boolean> deletePermission(@PathVariable Long id) {
        return CommonResult.success(auditService.deletePermission(id));
    }

    @Operation(summary = "检查账号是否可查看团队业绩")
    @GetMapping("/settings/can-view")
    public CommonResult<Boolean> canView(@RequestParam(required = false) Long agentId,
                                         @RequestParam(required = false) Long userId) {
        return CommonResult.success(auditService.canViewTeamPerformance(agentId, userId));
    }

    @Operation(summary = "根据代理ID或用户ID查询订单")
    @GetMapping("/orders")
    public CommonResult<CommonPage<OrderAuditVO>> getOrders(@RequestParam(required = false) String memberKey,
                                                           @RequestParam(required = false) String orderNo,
                                                           @RequestParam(required = false) Long agentId,
                                                           @RequestParam(required = false) Long userId,
                                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<OrderAuditVO> orders;
        if (orderNo != null && !orderNo.isBlank()) {
            orders = auditService.getOrdersByOrderNo(orderNo);
        } else if (memberKey != null && !memberKey.isBlank()) {
            orders = auditService.getOrdersByMemberKey(memberKey);
        } else if (agentId != null) {
            orders = auditService.getOrdersByAgentId(agentId);
        } else if (userId != null) {
            orders = auditService.getOrdersByUserId(userId);
        } else {
            orders = auditService.getAllOrders();
        }
        return CommonResult.success(CommonPage.restPage(orders));
    }

    @Operation(summary = "根据代理ID或用户ID查询奖金来源")
    @GetMapping("/bonus-sources")
    public CommonResult<CommonPage<CommissionRecordVO>> getBonusSources(@RequestParam(required = false) String memberKey,
                                                                        @RequestParam(required = false) String orderNo,
                                                                        @RequestParam(required = false) Long agentId,
                                                                        @RequestParam(required = false) Long userId,
                                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<CommissionRecordVO> records;
        if (orderNo != null && !orderNo.isBlank()) {
            records = auditService.getBonusSourcesByOrderNo(orderNo);
        } else if (memberKey != null && !memberKey.isBlank()) {
            records = auditService.getBonusSourcesByMemberKey(memberKey);
        } else if (agentId != null) {
            records = auditService.getBonusSourcesByAgentId(agentId);
        } else if (userId != null) {
            records = auditService.getBonusSourcesByUserId(userId);
        } else {
            records = auditService.getAllBonusSources();
        }
        return CommonResult.success(CommonPage.restPage(records));
    }

    @Operation(summary = "人员全景档案")
    @GetMapping("/person-profile")
    public CommonResult<PersonProfileVO> getPersonProfile(@RequestParam(required = false) Long agentId,
                                                          @RequestParam(required = false) Long userId,
                                                          @RequestParam(required = false) String keyword) {
        return CommonResult.success(auditService.getPersonProfile(agentId, userId, keyword));
    }

    @Operation(summary = "查询订单奖金流向与利润")
    @GetMapping("/orders/{orderId}/finance")
    public CommonResult<OrderFinanceDetailVO> getOrderFinance(@PathVariable Long orderId) {
        return CommonResult.success(auditService.getOrderFinanceDetail(orderId));
    }

    @Operation(summary = "保存订单支付金额和产品成本")
    @PutMapping("/orders/{orderId}/finance")
    public CommonResult<OrderFinanceVO> saveOrderFinance(@PathVariable Long orderId, @Valid @RequestBody OrderFinanceDTO dto) {
        dto.setOrderId(orderId);
        return CommonResult.success(auditService.upsertOrderFinance(dto));
    }

    @Operation(summary = "保存订单公司分账")
    @PutMapping("/orders/{orderId}/company-shares")
    public CommonResult<List<OrderCompanyShareVO>> saveCompanyShares(@PathVariable Long orderId,
                                                                     @Valid @Size(max = 100, message = "单个订单最多配置100条公司分账")
                                                                     @RequestBody List<@Valid OrderCompanyShareDTO> shares) {
        return CommonResult.success(auditService.saveCompanyShares(orderId, shares));
    }

    @Operation(summary = "财务总览汇总")
    @GetMapping("/finance/summary")
    public CommonResult<FinanceSummaryVO> getFinanceSummary(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return CommonResult.success(auditService.getFinanceSummary(range, startDate, endDate));
    }

    @Operation(summary = "财务每日趋势")
    @GetMapping("/finance/daily")
    public CommonResult<List<FinanceDailySummaryVO>> getFinanceDailySummary(
            @RequestParam(defaultValue = "7days") String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return CommonResult.success(auditService.getFinanceDailySummary(range, startDate, endDate));
    }

    @Operation(summary = "保存订单退款冲账")
    @PostMapping("/finance/refunds")
    public CommonResult<DmsFinanceRefund> saveRefund(@RequestBody FinanceRefundDTO dto) {
        return CommonResult.failed("退款必须从商城订单售后按商品和实际件数发起，财务页不允许手填退款金额");
    }

    @Operation(summary = "查询订单退款记录")
    @GetMapping("/orders/{orderId}/refunds")
    public CommonResult<List<DmsFinanceRefund>> getRefundsByOrderId(@PathVariable Long orderId) {
        return CommonResult.success(auditService.getRefundsByOrderId(orderId));
    }

    @Operation(summary = "公司账户分账汇总")
    @GetMapping("/finance/company-shares/summary")
    public CommonResult<List<CompanyShareSummaryVO>> getCompanyShareSummary(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return CommonResult.success(auditService.getCompanyShareSummary(range, startDate, endDate));
    }

    @Operation(summary = "查询财务风险规则")
    @GetMapping("/finance/risk-rules")
    public CommonResult<List<DmsFinanceRiskRule>> listRiskRules() {
        return CommonResult.success(auditService.listRiskRules());
    }

    @Operation(summary = "保存财务风险规则")
    @PostMapping("/finance/risk-rules")
    public CommonResult<DmsFinanceRiskRule> saveRiskRule(@Valid @RequestBody DmsFinanceRiskRule rule) {
        return CommonResult.success(auditService.saveRiskRule(rule));
    }

    @Operation(summary = "财务风险提醒")
    @GetMapping("/finance/risk-alerts")
    public CommonResult<List<FinanceRiskAlertVO>> getRiskAlerts(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return CommonResult.success(auditService.getRiskAlerts(range, startDate, endDate));
    }

    @Operation(summary = "导出财务日报表")
    @GetMapping("/finance/export")
    public void exportFinanceDailySummary(
            @RequestParam(defaultValue = "7days") String range,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        List<FinanceDailySummaryVO> rows = auditService.getFinanceDailySummary(range, startDate, endDate);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("财务日报");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"日期", "订单数", "成交额", "退款金额", "净收入", "产品成本", "奖金拨出", "公司分账", "公司利润", "风险订单"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (FinanceDailySummaryVO item : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getStatDate() == null ? "" : item.getStatDate().toString());
                row.createCell(1).setCellValue(item.getOrderCount() == null ? 0 : item.getOrderCount());
                row.createCell(2).setCellValue(toDouble(item.getPayAmount()));
                row.createCell(3).setCellValue(toDouble(item.getRefundAmount()));
                row.createCell(4).setCellValue(toDouble(item.getNetPayAmount()));
                row.createCell(5).setCellValue(toDouble(item.getProductCost()));
                row.createCell(6).setCellValue(toDouble(item.getBonusAmount()));
                row.createCell(7).setCellValue(toDouble(item.getCompanyShareAmount()));
                row.createCell(8).setCellValue(toDouble(item.getCompanyProfit()));
                row.createCell(9).setCellValue(item.getRiskOrderCount() == null ? 0 : item.getRiskOrderCount());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = URLEncoder.encode("财务日报.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            workbook.write(response.getOutputStream());
        }
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
