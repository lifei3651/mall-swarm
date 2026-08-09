package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderSpreadsheetServiceTest {

    private final OrderSpreadsheetService service = new OrderSpreadsheetService();

    @Test
    void shipmentTemplateKeepsOrderAndTrackingColumnsAsText() throws Exception {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(1L);
        order.setOrderNo("SO2083490924069793792");
        order.setStatus(1);
        order.setReceiverName("测试收货人");
        order.setReceiverPhone("13888888888");
        order.setReceiverAddress("湖南省长沙市测试地址");

        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setProductName("测试商品");
        item.setSkuName("默认规格");
        item.setQuantity(2);
        ShopOrderVO view = new ShopOrderVO();
        view.setOrder(order);
        view.setItems(List.of(item));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeShipmentTemplate(List.of(view), output);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("订单发货");
            assertNotNull(sheet);
            assertEquals(1, workbook.getNumberOfSheets());
            assertNull(workbook.getSheet("填写说明"));
            assertEquals("订单号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("物流公司", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("物流单号", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("发货数量", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals(4, sheet.getRow(0).getLastCellNum());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
            assertEquals("SO2083490924069793792", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("@", sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
            assertEquals(2D, sheet.getRow(1).getCell(3).getNumericCellValue());
            assertEquals(0, sheet.getDataValidations().size());
            assertEquals(0, sheet.getNumMergedRegions());
        }
    }

    @Test
    void shipmentImportTemplateKeepsDataSheetBlankAndPlacesExamplesInInstructions() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeShipmentImportTemplate(output);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            var dataSheet = workbook.getSheetAt(0);
            assertEquals("物流发货导入", dataSheet.getSheetName());
            assertEquals("订单号", dataSheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("物流公司", dataSheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("物流单号", dataSheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("发货数量", dataSheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals(0, dataSheet.getLastRowNum());

            var instructionSheet = workbook.getSheet("填写说明");
            assertNotNull(instructionSheet);
            assertEquals("填写项目", instructionSheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("订单号", instructionSheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("SF1234567890", instructionSheet.getRow(3).getCell(2).getStringCellValue());
        }
    }

    @Test
    void orderExportUsesPlainWorksheetWithoutDecorationsOrControls() throws Exception {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(1L);
        order.setOrderNo("L202608021230001234");
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.of(2026, 8, 2, 12, 30));
        order.setReceiverName("测试收货人");
        order.setReceiverPhone("13888888888");
        order.setReceiverAddress("湖南省长沙市测试地址");
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setFreightAmount(BigDecimal.ZERO);
        order.setPayAmount(new BigDecimal("99.00"));

        DmsShopOrderItem orderItem = new DmsShopOrderItem();
        orderItem.setProductName("测试商品");
        orderItem.setSkuName("默认规格");
        orderItem.setQuantity(1);

        ShopOrderVO view = new ShopOrderVO();
        view.setOrder(order);
        view.setMemberAccount("test_account");
        view.setItems(List.of(orderItem));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeOrderExport(List.of(view), output);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("订单明细");
            assertNotNull(sheet);
            assertNull(sheet.getPaneInformation());
            assertFalse(((XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter());
            assertEquals(0, sheet.getDataValidations().size());
            assertEquals(0, sheet.getNumMergedRegions());
            assertEquals(FillPatternType.NO_FILL, sheet.getRow(0).getCell(0).getCellStyle().getFillPattern());
            assertFalse(workbook.getFontAt(sheet.getRow(0).getCell(0).getCellStyle().getFontIndexAsInt()).getBold());
            assertEquals(2048, sheet.getColumnWidth(0));
            assertEquals("订单号", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("L202608021230001234", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("2026-08-02 12:30:00", sheet.getRow(1).getCell(2).getStringCellValue());
        }
    }
}
