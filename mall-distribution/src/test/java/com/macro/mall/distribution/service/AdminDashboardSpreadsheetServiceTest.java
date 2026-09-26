package com.macro.mall.distribution.service;

import com.macro.mall.distribution.vo.AdminDashboardVO;
import com.macro.mall.distribution.vo.DashboardTrendVO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminDashboardSpreadsheetServiceTest {

    private final AdminDashboardSpreadsheetService service = new AdminDashboardSpreadsheetService();

    @Test
    void exportsCurrentDashboardIntoAnalysisSheets() throws Exception {
        AdminDashboardVO dashboard = new AdminDashboardVO();
        dashboard.setTotalSalesAmount(new BigDecimal("1234.56"));
        dashboard.setRegisteredMemberCount(32L);
        dashboard.setPerformanceTrend(List.of(
                new DashboardTrendVO(LocalDate.of(2026, 8, 11), new BigDecimal("88.00"))));
        dashboard.setMonthlyPerformanceTrend(List.of());
        dashboard.setProductRanking(List.of());
        dashboard.setMemberRegionDistribution(List.of());
        dashboard.setLevelDistribution(List.of());
        dashboard.setLowStockProducts(List.of());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.write(dashboard, output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(7, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("经营概览"));
            assertNotNull(workbook.getSheet("近30天销售趋势"));
            assertNotNull(workbook.getSheet("月度销售趋势"));
            assertNotNull(workbook.getSheet("商品销售排行"));
            assertNotNull(workbook.getSheet("区域订单分布"));
            assertNotNull(workbook.getSheet("会员等级分布"));
            assertNotNull(workbook.getSheet("库存预警"));
            assertEquals("1234.56", workbook.getSheet("经营概览").getRow(2).getCell(1).getStringCellValue());
            assertEquals("累计净收款", workbook.getSheet("经营概览").getRow(5).getCell(0).getStringCellValue());
            assertEquals("未退商品成本、净奖金及公司分账", workbook.getSheet("经营概览").getRow(6).getCell(2).getStringCellValue());
            assertEquals("账面利润（含预计）", workbook.getSheet("经营概览").getRow(7).getCell(0).getStringCellValue());
            assertEquals("逐单利润汇总；全额退款归零，未冲销拨出另记风险", workbook.getSheet("经营概览").getRow(7).getCell(2).getStringCellValue());
            assertEquals(88D, workbook.getSheet("近30天销售趋势").getRow(1).getCell(1).getNumericCellValue());
        }
    }

    @Test
    void exportOmitsUnauthorizedGroupsInsteadOfWritingZeroFilledSheets() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.write(new AdminDashboardVO(), output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("经营概览"));
            assertEquals(1, workbook.getSheet("经营概览").getLastRowNum());
            assertNull(workbook.getSheet("近30天销售趋势"));
            assertNull(workbook.getSheet("商品销售排行"));
            assertNull(workbook.getSheet("区域订单分布"));
        }
    }
}
