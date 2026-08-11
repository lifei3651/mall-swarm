package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.AdminDashboardVO;
import com.macro.mall.distribution.vo.DashboardLevelCountVO;
import com.macro.mall.distribution.vo.DashboardLowStockVO;
import com.macro.mall.distribution.vo.DashboardProductRankingVO;
import com.macro.mall.distribution.vo.DashboardRegionVO;
import com.macro.mall.distribution.vo.DashboardTrendVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 工作台经营数据导出。工作台数据均为聚合结果，导出时不重新扫描业务明细。 */
@Service
public class AdminDashboardSpreadsheetService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void write(AdminDashboardVO dashboard, OutputStream outputStream) throws IOException {
        AdminDashboardVO data = dashboard == null ? new AdminDashboardVO() : dashboard;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            writeSummary(workbook, headerStyle, data);
            writeTrend(workbook, headerStyle, data.getPerformanceTrend(), "近30天销售趋势");
            writeTrend(workbook, headerStyle, data.getMonthlyPerformanceTrend(), "月度销售趋势");
            writeProducts(workbook, headerStyle, data.getProductRanking());
            writeRegions(workbook, headerStyle, data.getMemberRegionDistribution());
            writeLevels(workbook, headerStyle, data.getLevelDistribution());
            writeLowStock(workbook, headerStyle, data.getLowStockProducts());
            workbook.write(outputStream);
        }
    }

    private void writeSummary(XSSFWorkbook workbook, CellStyle headerStyle, AdminDashboardVO data) {
        Sheet sheet = workbook.createSheet("经营概览");
        writeHeader(sheet, headerStyle, "指标", "数值", "说明");
        int row = 1;
        row = summaryRow(sheet, row, "报表生成时间", DATE_TIME.format(LocalDateTime.now()), "导出时的实时经营快照");
        row = summaryRow(sheet, row, "累计销售额", money(data.getTotalSalesAmount()), "历史有效成交");
        row = summaryRow(sheet, row, "本月销售额", money(data.getMonthSalesAmount()), "本自然月累计");
        row = summaryRow(sheet, row, "今日销售额", money(data.getTodaySalesAmount()), "当日有效成交");
        row = summaryRow(sheet, row, "累计收款", money(data.getTotalReceiptAmount()), "有效资金收入");
        row = summaryRow(sheet, row, "累计支出", money(data.getTotalPayoutAmount()), "成本、奖金及其他有效支出");
        row = summaryRow(sheet, row, "累计利润", money(data.getTotalProfitAmount()), "累计收款减累计支出");
        row = summaryRow(sheet, row, "利润率", percent(data.getProfitRate()), "累计利润 / 累计收款");
        row = summaryRow(sheet, row, "注册会员", value(data.getRegisteredMemberCount()), "商城注册账号数");
        row = summaryRow(sheet, row, "有效会员", value(data.getValidMemberCount()), "已满足当前会员有效条件");
        row = summaryRow(sheet, row, "本月新增会员", value(data.getMonthNewMemberCount()), "本自然月新增");
        row = summaryRow(sheet, row, "待发放奖金", money(data.getUnsettledCommission()), value(data.getUnsettledCommissionCount()) + " 笔");
        summaryRow(sheet, row, "待审核提现", money(data.getPendingWithdrawAmount()), value(data.getPendingWithdrawCount()) + " 笔");
        setWidths(sheet, 24, 22, 42);
    }

    private void writeTrend(XSSFWorkbook workbook, CellStyle headerStyle, List<DashboardTrendVO> values, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        writeHeader(sheet, headerStyle, "统计日期", "有效销售额");
        int row = 1;
        for (DashboardTrendVO item : safe(values)) {
            Row current = sheet.createRow(row++);
            set(current, 0, item.getStatDate() == null ? "" : item.getStatDate().toString());
            set(current, 1, item.getPerformanceAmount());
        }
        setWidths(sheet, 20, 22);
    }

    private void writeProducts(XSSFWorkbook workbook, CellStyle headerStyle, List<DashboardProductRankingVO> values) {
        Sheet sheet = workbook.createSheet("商品销售排行");
        writeHeader(sheet, headerStyle, "排名", "商品ID", "商品名称", "成交订单", "销售数量", "销售额");
        int row = 1;
        for (DashboardProductRankingVO item : safe(values)) {
            Row current = sheet.createRow(row++);
            set(current, 0, item.getRanking());
            set(current, 1, item.getProductId());
            set(current, 2, item.getProductName());
            set(current, 3, item.getOrderCount());
            set(current, 4, item.getSalesQuantity());
            set(current, 5, item.getSalesAmount());
        }
        setWidths(sheet, 10, 16, 34, 16, 16, 20);
    }

    private void writeRegions(XSSFWorkbook workbook, CellStyle headerStyle, List<DashboardRegionVO> values) {
        Sheet sheet = workbook.createSheet("区域订单分布");
        writeHeader(sheet, headerStyle, "区域", "有效会员", "有效订单", "会员占比");
        int row = 1;
        for (DashboardRegionVO item : safe(values)) {
            Row current = sheet.createRow(row++);
            set(current, 0, item.getRegionName());
            set(current, 1, item.getMemberCount());
            set(current, 2, item.getOrderCount());
            set(current, 3, item.getPercentage() == null ? "0.00%" : item.getPercentage().setScale(2, java.math.RoundingMode.HALF_UP) + "%");
        }
        setWidths(sheet, 24, 16, 16, 16);
    }

    private void writeLevels(XSSFWorkbook workbook, CellStyle headerStyle, List<DashboardLevelCountVO> values) {
        Sheet sheet = workbook.createSheet("会员等级分布");
        writeHeader(sheet, headerStyle, "等级", "等级名称", "会员数量");
        int row = 1;
        for (DashboardLevelCountVO item : safe(values)) {
            Row current = sheet.createRow(row++);
            set(current, 0, item.getAgentLevel());
            set(current, 1, item.getLevelName());
            set(current, 2, item.getMemberCount());
        }
        setWidths(sheet, 12, 22, 16);
    }

    private void writeLowStock(XSSFWorkbook workbook, CellStyle headerStyle, List<DashboardLowStockVO> values) {
        Sheet sheet = workbook.createSheet("库存预警");
        writeHeader(sheet, headerStyle, "商品ID", "商品名称", "SKU ID", "规格", "当前库存", "安全库存");
        int row = 1;
        for (DashboardLowStockVO item : safe(values)) {
            Row current = sheet.createRow(row++);
            set(current, 0, item.getProductId());
            set(current, 1, item.getProductName());
            set(current, 2, item.getSkuId());
            set(current, 3, item.getSkuName());
            set(current, 4, item.getStock());
            set(current, 5, item.getSafetyStock());
        }
        setWidths(sheet, 16, 32, 16, 24, 16, 16);
    }

    private int summaryRow(Sheet sheet, int rowIndex, String name, String value, String note) {
        Row row = sheet.createRow(rowIndex);
        set(row, 0, name);
        set(row, 1, value);
        set(row, 2, note);
        return rowIndex + 1;
    }

    private void writeHeader(Sheet sheet, CellStyle style, String... columns) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void set(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue());
        else cell.setCellValue(value == null ? "" : String.valueOf(value));
    }

    private void setWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String percent(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String value(Number value) {
        return value == null ? "0" : String.valueOf(value.longValue());
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
