package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsMerchantDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.service.impl.OrderShipmentServiceImpl;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.OrderShipmentImportResultVO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderShipmentServiceTest {

    @Mock private DmsShopOrderDao orderDao;
    @Mock private DmsShopOrderItemDao orderItemDao;
    @Mock private DmsShopAfterSaleDao afterSaleDao;
    @Mock private DmsShopAfterSaleItemDao afterSaleItemDao;
    @Mock private DmsShopOrderShipmentDao shipmentDao;
    @Mock private DmsMerchantDao merchantDao;
    @Mock private OperationLogService operationLogService;

    private OrderShipmentServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        service = new OrderShipmentServiceImpl(orderDao, orderItemDao, afterSaleDao, afterSaleItemDao,
                shipmentDao, operationLogService);
        ReflectionTestUtils.setField(service, "merchantDao", merchantDao);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        AdminContext.clear();
    }

    @Test
    void importsValidShipmentAndMarksOrderShippedOnce() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(1);
        when(shipmentDao.insert(any(DmsShopOrderShipment.class))).thenReturn(1);
        when(orderDao.ship(11L, "顺丰速运", "SF1234567890")).thenReturn(1);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "1"}));

        assertTrue(result.isSuccess());
        assertEquals(1, result.getShippedCount());
        assertEquals(0, result.getFailedCount());
        verify(orderDao).ship(11L, "顺丰速运", "SF1234567890");
    }

    @Test
    void blankCarrierUsesCurrentAccountsDefaultLogisticsCompany() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(1);
        when(shipmentDao.insert(any(DmsShopOrderShipment.class))).thenReturn(1);
        when(orderDao.ship(11L, "京东物流", "JD1234567890")).thenReturn(1);
        DmsAdminUser operator = new DmsAdminUser();
        operator.setId(9001L);
        operator.setDefaultLogisticsCompany("京东物流");
        AdminContext.set(operator);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "", "JD1234567890", "1"}));

        assertTrue(result.isSuccess());
        verify(orderDao).ship(11L, "京东物流", "JD1234567890");
    }

    @Test
    void invalidRowIsSkippedWithoutBlockingValidShipments() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(1);
        when(shipmentDao.insert(any(DmsShopOrderShipment.class))).thenReturn(1);
        when(orderDao.ship(11L, "顺丰速运", "SF1234567890")).thenReturn(1);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "1"},
                new String[]{"SO10002", "中通快递", "", "1"}));

        assertFalse(result.isSuccess());
        assertEquals(1, result.getShippedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(3, result.getErrors().get(0).getRowNumber());
        assertTrue(result.getMessage().contains("正确行已正常发货"));
        verify(orderDao).ship(11L, "顺丰速运", "SF1234567890");
        verify(shipmentDao).insert(any(DmsShopOrderShipment.class));
    }

    @Test
    void repeatedImportWithSameTrackingIsIdempotentlySkipped() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        order.setStatus(2);
        order.setDeliveryCompany("顺丰速运");
        order.setDeliveryNo("SF1234567890");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "1"}));

        assertTrue(result.isSuccess());
        assertEquals(0, result.getShippedCount());
        assertEquals(1, result.getSkippedCount());
        verify(orderDao, never()).ship(anyLong(), anyString(), anyString());
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
    }

    @Test
    void oneOrderCanBeSplitIntoMultiplePackages() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(2);
        when(shipmentDao.sumQuantityByOrderId(11L)).thenReturn(0, 0, 1);
        when(shipmentDao.insert(any(DmsShopOrderShipment.class))).thenReturn(1);
        when(orderDao.ship(11L, "中通快递", "ZT00000002")).thenReturn(1);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF00000001", "1"},
                new String[]{"SO10001", "中通快递", "ZT00000002", "1"}));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getShippedCount());
        verify(shipmentDao, org.mockito.Mockito.times(2)).insert(any(DmsShopOrderShipment.class));
        verify(orderDao, org.mockito.Mockito.times(1)).ship(anyLong(), anyString(), anyString());
    }

    @Test
    void onePackageCanBeSharedByMultipleOrders() throws Exception {
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(pendingOrder(11L, "SO10001"));
        when(orderDao.selectByOrderNoForUpdate("SO10002")).thenReturn(pendingOrder(12L, "SO10002"));
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(1);
        when(orderItemDao.sumQuantityByOrderId(12L)).thenReturn(1);
        when(shipmentDao.insert(any(DmsShopOrderShipment.class))).thenReturn(1);
        when(orderDao.ship(anyLong(), anyString(), anyString())).thenReturn(1);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF-SHARED-001", "1"},
                new String[]{"SO10002", "顺丰速运", "SF-SHARED-001", "1"}));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getShippedCount());
        verify(shipmentDao, org.mockito.Mockito.times(2)).insert(any(DmsShopOrderShipment.class));
        verify(orderDao, org.mockito.Mockito.times(2)).ship(anyLong(), anyString(), anyString());
    }

    @Test
    void shipmentQuantityCannotExceedOrderedQuantity() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(2);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "3"}));

        assertFalse(result.isSuccess());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrors().get(0).getMessage().contains("剩余 2 件"));
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
    }

    @Test
    void openAfterSaleBlocksManualShipmentBeforeAnyPackageIsCreated() {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByIdForUpdate(11L)).thenReturn(order);
        when(afterSaleDao.selectOpenByOrderId(11L)).thenReturn(new DmsShopAfterSale());

        var dto = new com.macro.mall.distribution.dto.ShopOrderShipDTO();
        dto.setDeliveryCompany("顺丰速运");
        dto.setDeliveryNo("SF1234567890");
        dto.setShipmentQuantity(1);

        var error = assertThrows(com.macro.mall.common.exception.ApiException.class,
                () -> service.shipOrder(11L, dto));

        assertTrue(error.getMessage().contains("售后处理中"));
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
        verify(orderDao, never()).ship(anyLong(), anyString(), anyString());
    }

    @Test
    void completedPartialRefundReducesRemainingShippableQuantity() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        when(orderItemDao.sumQuantityByOrderId(11L)).thenReturn(3);
        when(afterSaleItemDao.sumApprovedQuantityByOrderId(11L)).thenReturn(1);

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "3"}));

        assertFalse(result.isSuccess());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getErrors().get(0).getMessage().contains("剩余 2 件"));
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
    }

    @Test
    void merchantBatchImportCannotShipAnotherMerchantsOrder() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        order.setMerchantId(8002L);
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        AdminContext.set(merchantAdmin(8001L));

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "1"}));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).getMessage().contains("其他商户"));
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
    }

    @Test
    void platformTakeoverBlocksMerchantBatchShipment() throws Exception {
        DmsShopOrder order = pendingOrder(11L, "SO10001");
        order.setMerchantId(8001L);
        when(orderDao.selectByOrderNoForUpdate("SO10001")).thenReturn(order);
        DmsMerchant merchant = new DmsMerchant();
        merchant.setId(8001L);
        merchant.setFulfillmentStatus("PLATFORM_ONLY");
        when(merchantDao.selectById(8001L)).thenReturn(merchant);
        AdminContext.set(merchantAdmin(8001L));

        OrderShipmentImportResultVO result = service.importShipments(workbook(
                new String[]{"SO10001", "顺丰速运", "SF1234567890", "1"}));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).getMessage().contains("平台接管或冻结"));
        verify(shipmentDao, never()).insert(any(DmsShopOrderShipment.class));
    }

    private DmsShopOrder pendingOrder(Long id, String orderNo) {
        DmsShopOrder order = new DmsShopOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setTenantId(1L);
        order.setStatus(1);
        return order;
    }

    private DmsAdminUser merchantAdmin(Long merchantId) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9001L);
        admin.setMerchantId(merchantId);
        return admin;
    }

    private MockMultipartFile workbook(String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("订单发货");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("订单号");
            header.createCell(1).setCellValue("物流公司");
            header.createCell(2).setCellValue("物流单号");
            header.createCell(3).setCellValue("发货数量");
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int column = 0; column < rows[i].length; column++) {
                    row.createCell(column).setCellValue(rows[i][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "订单发货.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
