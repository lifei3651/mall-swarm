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

class AdminDashboardSpreadsheetServiceTest {

    private final AdminDashboardSpreadsheetService service = new AdminDashboardSpreadsheetService();

    @Test
    void exportsCurrentDashboardIntoAnalysisSheets() throws Exception {
        AdminDashboardVO dashboard = new AdminDashboardVO();
        dashboard.setTotalSalesAmount(new BigDecimal("1234.56"));
        dashboard.setRegisteredMemberCount(32L);
        dashboard.setPerformanceTrend(List.of(
                new DashboardTrendVO(LocalDate.of(2026, 8, 11), new BigDecimal("88.00"))));

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
            assertEquals(88D, workbook.getSheet("近30天销售趋势").getRow(1).getCell(1).getNumericCellValue());
        }
    }
}
