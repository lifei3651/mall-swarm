package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dto.ShopOrderShipDTO;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.OrderShipmentService;
import com.macro.mall.distribution.vo.OrderShipmentImportResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderShipmentServiceImpl implements OrderShipmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_DATA_ROWS = 2000;
    private static final int MAX_COMPANY_LENGTH = 50;
    private static final int MAX_DELIVERY_NO_LENGTH = 64;

    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderShipmentDao shipmentDao;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean shipOrder(Long orderId, ShopOrderShipDTO dto) {
        if (orderId == null) Asserts.fail("订单ID不能为空");
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null) Asserts.fail("订单不存在");
        assertTenant(order);
        ShipmentValues probe = normalize(dto == null ? null : dto.getDeliveryCompany(),
                dto == null ? null : dto.getDeliveryNo(), 1);
        if (shipmentExists(order, probe)) return true;
        Integer quantity = dto == null ? null : dto.getShipmentQuantity();
        ShipmentValues shipment = normalize(probe.company(), probe.deliveryNo(),
                quantity == null ? remainingQuantity(order) : quantity);
        return applyShipment(order, shipment, "MANUAL", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean shipErpOrder(String orderNo, String deliveryCompany, String deliveryNo,
                                Integer shipmentQuantity, String providerCode) {
        String normalizedOrderNo = trim(orderNo);
        if (normalizedOrderNo == null) Asserts.fail("商城订单号不能为空");
        DmsShopOrder order = orderDao.selectByOrderNoForUpdate(normalizedOrderNo);
        if (order == null) Asserts.fail("商城订单不存在");
        assertTenant(order);
        ShipmentValues probe = normalize(deliveryCompany, deliveryNo, 1);
        if (shipmentExists(order, probe)) return true;
        ShipmentValues shipment = normalize(probe.company(), probe.deliveryNo(),
                shipmentQuantity == null ? remainingQuantity(order) : shipmentQuantity);
        return applyShipment(order, shipment, "ERP:" + safeSource(providerCode), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderShipmentImportResultVO importShipments(MultipartFile file) {
        List<ShipmentRow> rows = readRows(file);
        OrderShipmentImportResultVO result = new OrderShipmentImportResultVO();
        result.setTotalRows(rows.size());
        if (rows.isEmpty()) {
            result.setFailedCount(1);
            result.getErrors().add(new OrderShipmentImportResultVO.RowError(2, null, "表格中没有可导入的发货数据"));
            return finishFailure(result);
        }

        Set<String> shipmentRows = new HashSet<>();
        Map<String, DmsShopOrder> ordersByNo = new HashMap<>();
        Map<String, Integer> orderedQuantities = new HashMap<>();
        Map<String, Integer> shippedQuantities = new HashMap<>();
        Map<String, Integer> batchQuantities = new HashMap<>();
        List<PreparedShipment> prepared = new ArrayList<>();
        for (ShipmentRow row : rows) {
            String orderNo = trim(row.orderNo());
            ShipmentValues shipment = null;
            if (orderNo == null) {
                addError(result, row, "订单号不能为空");
                continue;
            }
            try {
                shipment = normalize(row.deliveryCompany(), row.deliveryNo(), parseQuantity(row.shipmentQuantity()));
            } catch (IllegalArgumentException ex) {
                addError(result, row, ex.getMessage());
                continue;
            }

            String shipmentRowKey = orderNo.toLowerCase(Locale.ROOT) + "\n"
                    + shipment.company().toLowerCase(Locale.ROOT) + "\n"
                    + shipment.deliveryNo().toLowerCase(Locale.ROOT);
            if (!shipmentRows.add(shipmentRowKey)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            DmsShopOrder order = ordersByNo.get(orderNo);
            if (order == null) {
                order = orderDao.selectByOrderNoForUpdate(orderNo);
                if (order != null) ordersByNo.put(orderNo, order);
            }
            if (order == null) {
                addError(result, row, "商城订单不存在");
                continue;
            }
            if (!sameTenant(order)) {
                addError(result, row, "无权操作当前租户订单");
                continue;
            }
            if (shipmentExists(order, shipment)) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            if (hasOpenAfterSale(order)) {
                addError(result, row, openAfterSaleShipmentMessage());
                continue;
            }
            if (!canAddShipment(order)) {
                addError(result, row, Integer.valueOf(3).equals(order.getStatus())
                        ? "订单已经完成，不能再添加物流包裹" : "当前订单状态不能发货");
                continue;
            }
            if (!orderedQuantities.containsKey(orderNo)) {
                orderedQuantities.put(orderNo, shippableQuantity(order));
                shippedQuantities.put(orderNo, shipmentDao.sumQuantityByOrderId(order.getId()));
            }
            int orderedQuantity = orderedQuantities.get(orderNo);
            int shippedQuantity = shippedQuantities.get(orderNo);
            int batchQuantity = batchQuantities.getOrDefault(orderNo, 0);
            if (orderedQuantity <= 0) {
                addError(result, row, "订单没有可发货的商品明细");
                continue;
            }
            if (shippedQuantity + batchQuantity + shipment.quantity() > orderedQuantity) {
                addError(result, row, "发货数量超过订单剩余可发件数（剩余 "
                        + Math.max(0, orderedQuantity - shippedQuantity - batchQuantity) + " 件）");
                continue;
            }
            batchQuantities.put(orderNo, batchQuantity + shipment.quantity());
            prepared.add(new PreparedShipment(row, order, shipment));
        }

        for (PreparedShipment item : prepared) {
            applyShipment(item.order(), item.shipment(), "EXCEL_IMPORT", true);
            result.setShippedCount(result.getShippedCount() + 1);
        }
        result.setFailedCount(result.getErrors().size());
        result.setSuccess(result.getFailedCount() == 0);
        if (result.getFailedCount() > 0) {
            result.setMessage("导入完成：新增 " + result.getShippedCount() + " 条订单包裹记录，重复跳过 "
                    + result.getSkippedCount() + " 条，错误跳过 " + result.getFailedCount()
                    + " 条；正确行已正常发货，请按错误明细修正失败行后重新导入");
        } else {
            result.setMessage("批量发货完成：新增 " + result.getShippedCount() + " 条订单包裹记录，重复跳过 "
                    + result.getSkippedCount() + " 条");
        }
        operationLogService.log("SHOP_ORDER", "SHIPMENT_IMPORT", "SHOP_ORDER_BATCH", "BATCH",
                null, "total=" + result.getTotalRows() + ", shipped=" + result.getShippedCount()
                        + ", skipped=" + result.getSkippedCount() + ", failed=" + result.getFailedCount(),
                "Excel批量导入订单物流信息");
        return result;
    }

    private boolean applyShipment(DmsShopOrder order, ShipmentValues shipment, String source, boolean failOnConflict) {
        if (shipmentExists(order, shipment)) return true;
        if (hasOpenAfterSale(order)) {
            if (failOnConflict) Asserts.fail(openAfterSaleShipmentMessage());
            return false;
        }
        if (!canAddShipment(order)) {
            if (failOnConflict) {
                Asserts.fail(Integer.valueOf(3).equals(order.getStatus())
                        ? "订单已经完成，不能再添加物流包裹" : "当前订单状态不能发货");
            }
            return false;
        }

        int orderedQuantity = shippableQuantity(order);
        int shippedQuantity = shipmentDao.sumQuantityByOrderId(order.getId());
        if (orderedQuantity <= 0) Asserts.fail("订单没有可发货的商品明细");
        if (shippedQuantity + shipment.quantity() > orderedQuantity) {
            Asserts.fail("发货数量超过订单剩余可发件数（剩余 "
                    + Math.max(0, orderedQuantity - shippedQuantity) + " 件）");
        }

        DmsShopOrderShipment record = new DmsShopOrderShipment();
        record.setTenantId(order.getTenantId() == null ? 1L : order.getTenantId());
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setDeliveryCompany(shipment.company());
        record.setDeliveryNo(shipment.deliveryNo());
        record.setShipmentQuantity(shipment.quantity());
        record.setSource(source);
        record.setDeliveryTime(java.time.LocalDateTime.now());
        if (shipmentDao.insert(record) <= 0) {
            Asserts.fail("保存物流包裹失败，请稍后重试");
        }

        if (Integer.valueOf(1).equals(order.getStatus())
                && shippedQuantity + shipment.quantity() >= orderedQuantity) {
            if (orderDao.ship(order.getId(), shipment.company(), shipment.deliveryNo()) <= 0) {
                Asserts.fail("订单状态已变化，请刷新后重试");
            }
            // 旧字段保留一件物流包裹，兼容尚未升级的页面和历史接口。
            order.setStatus(2);
            order.setDeliveryCompany(shipment.company());
            order.setDeliveryNo(shipment.deliveryNo());
            order.setDeliveryTime(record.getDeliveryTime());
        }
        logShipment(order, shipment, source);
        return true;
    }

    private void logShipment(DmsShopOrder order, ShipmentValues shipment, String source) {
        operationLogService.log("SHOP_ORDER", "SHIP", "SHOP_ORDER", String.valueOf(order.getId()),
                "status=" + order.getStatus() + ", deliveryCompany=" + trim(order.getDeliveryCompany())
                        + ", deliveryNo=" + trim(order.getDeliveryNo()),
                "status=" + order.getStatus() + ", deliveryCompany=" + shipment.company()
                        + ", deliveryNo=" + shipment.deliveryNo() + ", shipmentQuantity=" + shipment.quantity(),
                "商城订单发货，来源=" + source + "，订单号=" + order.getOrderNo());
    }

    private List<ShipmentRow> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) Asserts.fail("导入文件不能为空");
        if (file.getSize() > MAX_FILE_SIZE) Asserts.fail("导入文件不能超过10MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) Asserts.fail("仅支持Excel格式（.xlsx或.xls）");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) Asserts.fail("Excel文件没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) Asserts.fail("Excel表头不能为空");
            DataFormatter formatter = new DataFormatter(Locale.SIMPLIFIED_CHINESE);
            Map<String, Integer> columns = new LinkedHashMap<>();
            for (int i = 0; i < Math.max(0, header.getLastCellNum()); i++) {
                String value = trim(formatter.formatCellValue(header.getCell(i)));
                if (value != null) columns.putIfAbsent(value, i);
            }
            int orderColumn = requiredColumn(columns, "订单号", "订单编号");
            int companyColumn = requiredColumn(columns, "物流公司");
            int deliveryNoColumn = requiredColumn(columns, "物流单号", "快递单号");
            int quantityColumn = requiredColumn(columns, "发货数量", "发货件数");
            List<ShipmentRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String orderNo = cellText(row.getCell(orderColumn), formatter);
                String company = cellText(row.getCell(companyColumn), formatter);
                String deliveryNo = cellText(row.getCell(deliveryNoColumn), formatter);
                String shipmentQuantity = cellText(row.getCell(quantityColumn), formatter);
                if (orderNo == null && company == null && deliveryNo == null && shipmentQuantity == null) continue;
                rows.add(new ShipmentRow(rowIndex + 1, orderNo, company, deliveryNo, shipmentQuantity));
                if (rows.size() > MAX_DATA_ROWS) Asserts.fail("单次最多导入2000条发货记录");
            }
            return rows;
        } catch (ApiException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            Asserts.fail("解析Excel失败，请使用系统下载的发货模板：" + ex.getMessage());
            return List.of();
        }
    }

    private int requiredColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null) return index;
        }
        Asserts.fail("Excel缺少必填列：" + names[0]);
        return -1;
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        return cell == null ? null : trim(formatter.formatCellValue(cell));
    }

    private ShipmentValues normalize(String company, String deliveryNo, Integer quantity) {
        String normalizedCompany = trim(company);
        String normalizedDeliveryNo = trim(deliveryNo);
        if (normalizedCompany == null) throw new IllegalArgumentException("物流公司不能为空");
        if (normalizedCompany.length() > MAX_COMPANY_LENGTH) throw new IllegalArgumentException("物流公司不能超过50个字符");
        if (normalizedDeliveryNo == null) throw new IllegalArgumentException("物流单号不能为空");
        if (normalizedDeliveryNo.length() < 4 || normalizedDeliveryNo.length() > MAX_DELIVERY_NO_LENGTH) {
            throw new IllegalArgumentException("物流单号长度需要4至64个字符");
        }
        if (!normalizedDeliveryNo.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("物流单号只能包含字母、数字、下划线和短横线");
        }
        if (quantity == null) throw new IllegalArgumentException("发货数量不能为空");
        if (quantity <= 0) throw new IllegalArgumentException("发货数量必须是大于0的整数");
        return new ShipmentValues(normalizedCompany, normalizedDeliveryNo, quantity);
    }

    private Integer parseQuantity(String value) {
        String normalized = trim(value);
        if (normalized == null) throw new IllegalArgumentException("发货数量不能为空");
        try {
            BigDecimal number = new BigDecimal(normalized).stripTrailingZeros();
            if (number.scale() > 0) throw new ArithmeticException();
            return number.intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("发货数量必须是大于0的整数");
        }
    }

    private int remainingQuantity(DmsShopOrder order) {
        int ordered = shippableQuantity(order);
        int shipped = shipmentDao.sumQuantityByOrderId(order.getId());
        int remaining = ordered - shipped;
        if (remaining <= 0) Asserts.fail("该订单已经没有待发货商品");
        return remaining;
    }

    private boolean canAddShipment(DmsShopOrder order) {
        return Integer.valueOf(1).equals(order.getStatus()) || Integer.valueOf(2).equals(order.getStatus());
    }

    private boolean hasOpenAfterSale(DmsShopOrder order) {
        return order != null && afterSaleDao.selectOpenByOrderId(order.getId()) != null;
    }

    private int shippableQuantity(DmsShopOrder order) {
        int ordered = orderItemDao.sumQuantityByOrderId(order.getId());
        int refunded = afterSaleItemDao.sumApprovedQuantityByOrderId(order.getId());
        return Math.max(0, ordered - refunded);
    }

    private String openAfterSaleShipmentMessage() {
        return "订单正在售后处理中，暂不能发货；售后取消或驳回后可继续发货";
    }

    private boolean shipmentExists(DmsShopOrder order, ShipmentValues shipment) {
        if (shipment.company().equalsIgnoreCase(trim(order.getDeliveryCompany()))
                && shipment.deliveryNo().equalsIgnoreCase(trim(order.getDeliveryNo()))) {
            return true;
        }
        return shipmentDao.selectByOrderAndTracking(order.getId(), shipment.company(), shipment.deliveryNo()) != null;
    }

    private void assertTenant(DmsShopOrder order) {
        if (!sameTenant(order)) Asserts.fail("无权访问当前租户数据");
    }

    private boolean sameTenant(DmsShopOrder order) {
        Long orderTenantId = order.getTenantId() == null ? 1L : order.getTenantId();
        return TenantContext.getTenantId().equals(orderTenantId);
    }

    private void addError(OrderShipmentImportResultVO result, ShipmentRow row, String message) {
        result.getErrors().add(new OrderShipmentImportResultVO.RowError(row.rowNumber(), trim(row.orderNo()), message));
    }

    private OrderShipmentImportResultVO finishFailure(OrderShipmentImportResultVO result) {
        result.setSuccess(false);
        result.setShippedCount(0);
        result.setFailedCount(result.getErrors().size());
        result.setMessage("没有可导入的发货数据；系统只读取订单号、物流公司、物流单号和发货数量");
        return result;
    }

    private String safeSource(String source) {
        String normalized = trim(source);
        return normalized == null ? "UNKNOWN" : normalized.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ShipmentValues(String company, String deliveryNo, int quantity) {}

    private record ShipmentRow(int rowNumber, String orderNo, String deliveryCompany, String deliveryNo,
                               String shipmentQuantity) {}

    private record PreparedShipment(ShipmentRow row, DmsShopOrder order, ShipmentValues shipment) {}
}
