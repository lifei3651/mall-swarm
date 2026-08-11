package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrderSpreadsheetService {

    public static final int MAX_SHIPMENT_TEMPLATE_ROWS = 2000;
    public static final int ORDER_EXPORT_PAGE_SIZE = 500;
    public static final int MAX_ORDER_EXPORT_ROWS = 1_000_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void writeOrderExport(List<ShopOrderVO> orders, OutputStream outputStream) throws IOException {
        writeOrderExport((pageNum, pageSize) -> pageNum == 1 ? orders : List.of(), outputStream);
    }

    /** 分页读取并流式写入，避免大数据量导出时一次性把全部订单和明细加载到内存。 */
    public void writeOrderExport(OrderPageLoader pageLoader, OutputStream outputStream) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("订单明细");
            String[] headers = {"订单号", "订单状态", "下单时间", "支付时间", "下单会员登录账号", "收货人", "手机号", "收货地址",
                    "商品明细", "商品总数", "商品金额", "运费", "实付金额", "支付方式", "物流公司", "物流单号", "发货数量", "发货时间", "订单备注"};
            writePlainHeader(sheet, headers);

            int rowIndex = 1;
            int pageNum = 1;
            while (true) {
                List<ShopOrderVO> orders = pageLoader.load(pageNum++, ORDER_EXPORT_PAGE_SIZE);
                if (orders == null || orders.isEmpty()) break;
                for (ShopOrderVO item : orders) {
                    DmsShopOrder order = item == null ? null : item.getOrder();
                    if (order == null) continue;
                    if (rowIndex > MAX_ORDER_EXPORT_ROWS) {
                        throw new IOException("导出订单超过100万条，请缩小筛选范围后分批导出");
                    }
                    Row row = sheet.createRow(rowIndex++);
                    setText(row, 0, order.getOrderNo());
                    setText(row, 1, statusName(order.getStatus()));
                    setText(row, 2, dateTime(order.getCreateTime()));
                    setText(row, 3, dateTime(order.getPayTime()));
                    setText(row, 4, item.getMemberAccount());
                    setText(row, 5, order.getReceiverName());
                    setText(row, 6, order.getReceiverPhone());
                    setText(row, 7, order.getReceiverAddress());
                    setText(row, 8, productSummary(item.getItems()));
                    setNumber(row, 9, itemCount(item.getItems()), null);
                    setNumber(row, 10, order.getTotalAmount(), null);
                    setNumber(row, 11, order.getFreightAmount(), null);
                    setNumber(row, 12, order.getPayAmount(), null);
                    setText(row, 13, payTypeName(order.getPayType()));
                    setText(row, 14, shipmentSummary(item, ShipmentField.COMPANY));
                    setText(row, 15, shipmentSummary(item, ShipmentField.TRACKING_NO));
                    setText(row, 16, shipmentSummary(item, ShipmentField.QUANTITY));
                    setText(row, 17, shipmentSummary(item, ShipmentField.DELIVERY_TIME));
                    setText(row, 18, order.getRemark());
                }
            }
            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    @FunctionalInterface
    public interface OrderPageLoader {
        List<ShopOrderVO> load(int pageNum, int pageSize);
    }

    public void writeShipmentTemplate(List<ShopOrderVO> orders, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("订单发货");
            String[] headers = {"订单号", "物流公司", "物流单号", "发货数量"};
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            writePlainHeader(sheet, headers);

            int rowIndex = 1;
            for (ShopOrderVO item : orders) {
                DmsShopOrder order = item == null ? null : item.getOrder();
                if (order == null || !Integer.valueOf(1).equals(order.getStatus())) continue;
                Row row = sheet.createRow(rowIndex++);
                setText(row, 0, order.getOrderNo(), textStyle);
                setText(row, 1, "", textStyle);
                setText(row, 2, "", textStyle);
                setNumber(row, 3, remainingShipmentQuantity(item), null);
            }
            workbook.write(outputStream);
        }
    }

    /**
     * 生成不包含任何真实订单的物流导入空白模板。
     * 第一张工作表严格保持为导入程序读取的数据表，示例和规则放在第二张工作表，
     * 避免客户未删除示例行便直接导入，造成错误发货。
     */
    public void writeShipmentImportTemplate(OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet dataSheet = workbook.createSheet("物流发货导入");
            writePlainHeader(dataSheet, new String[]{"订单号", "物流公司", "物流单号", "发货数量"});
            dataSheet.setColumnWidth(0, 30 * 256);
            dataSheet.setColumnWidth(1, 20 * 256);
            dataSheet.setColumnWidth(2, 26 * 256);
            dataSheet.setColumnWidth(3, 14 * 256);

            Sheet instructionSheet = workbook.createSheet("填写说明");
            writePlainHeader(instructionSheet, new String[]{"填写项目", "填写要求", "示例"});
            writeInstructionRow(instructionSheet, 1, "订单号", "必填；填写商城后台显示的完整订单号", "L202608091234567890");
            writeInstructionRow(instructionSheet, 2, "物流公司", "必填；最多50个字符", "顺丰速运");
            writeInstructionRow(instructionSheet, 3, "物流单号", "必填；4至64位，只能包含字母、数字、下划线和短横线", "SF1234567890");
            writeInstructionRow(instructionSheet, 4, "发货数量", "必填；填写大于0的整数，不能超过订单剩余可发件数", "1");
            writeInstructionRow(instructionSheet, 5, "拆分包裹", "同一订单分多个包裹时，复制订单号并分行填写不同物流信息", "每个包裹一行");
            writeInstructionRow(instructionSheet, 6, "合并包裹", "多个订单共用一个包裹时，每个订单各填一行，可填写相同物流信息", "每个订单一行");
            instructionSheet.setColumnWidth(0, 18 * 256);
            instructionSheet.setColumnWidth(1, 66 * 256);
            instructionSheet.setColumnWidth(2, 28 * 256);
            workbook.write(outputStream);
        }
    }

    private void writeInstructionRow(Sheet sheet, int rowIndex, String item, String requirement, String example) {
        Row row = sheet.createRow(rowIndex);
        setText(row, 0, item);
        setText(row, 1, requirement);
        setText(row, 2, example);
    }

    private void writePlainHeader(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    private void setText(Row row, int column, String value) {
        setText(row, column, value, null);
    }

    private void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    private void setNumber(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        if (style != null) cell.setCellStyle(style);
    }

    private String productSummary(List<DmsShopOrderItem> items) {
        if (items == null || items.isEmpty()) return "";
        return items.stream().filter(Objects::nonNull).map(item -> {
            String sku = item.getSkuName() == null || item.getSkuName().isBlank() ? "默认规格" : item.getSkuName();
            return Objects.toString(item.getProductName(), "商品") + "[" + sku + "]×"
                    + Objects.toString(item.getQuantity(), "0");
        }).collect(Collectors.joining("；"));
    }

    private int itemCount(List<DmsShopOrderItem> items) {
        if (items == null) return 0;
        return items.stream().filter(Objects::nonNull).map(DmsShopOrderItem::getQuantity)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private String shipmentSummary(ShopOrderVO item, ShipmentField field) {
        List<DmsShopOrderShipment> shipments = item == null ? null : item.getShipments();
        if (shipments != null && !shipments.isEmpty()) {
            return shipments.stream().filter(Objects::nonNull).map(shipment -> switch (field) {
                case COMPANY -> Objects.toString(shipment.getDeliveryCompany(), "");
                case TRACKING_NO -> Objects.toString(shipment.getDeliveryNo(), "");
                case QUANTITY -> Objects.toString(shipment.getShipmentQuantity(), "");
                case DELIVERY_TIME -> dateTime(shipment.getDeliveryTime());
            }).collect(Collectors.joining("\n"));
        }
        DmsShopOrder order = item == null ? null : item.getOrder();
        if (order == null) return "";
        return switch (field) {
            case COMPANY -> Objects.toString(order.getDeliveryCompany(), "");
            case TRACKING_NO -> Objects.toString(order.getDeliveryNo(), "");
            case QUANTITY -> order.getDeliveryNo() == null ? "" : String.valueOf(itemCount(item.getItems()));
            case DELIVERY_TIME -> dateTime(order.getDeliveryTime());
        };
    }

    private int remainingShipmentQuantity(ShopOrderVO item) {
        int ordered = itemCount(item == null ? null : item.getItems());
        int shipped = item == null || item.getShipments() == null ? 0 : item.getShipments().stream()
                .filter(Objects::nonNull)
                .map(DmsShopOrderShipment::getShipmentQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return Math.max(0, ordered - shipped);
    }

    private enum ShipmentField {
        COMPANY, TRACKING_NO, QUANTITY, DELIVERY_TIME
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "售后关闭";
            default -> "处理中";
        };
    }

    private String payTypeName(String payType) {
        if (payType == null) return "";
        return switch (payType.toUpperCase(Locale.ROOT)) {
            case "WECHAT" -> "微信支付";
            case "ALIPAY" -> "支付宝";
            case "BALANCE" -> "余额支付";
            default -> payType;
        };
    }
}
