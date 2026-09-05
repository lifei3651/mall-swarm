package com.macro.mall.distribution.util;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleItemDTO;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure input checks: run before database locks, queries or stock/financial mutations. */
public final class ShopQuantityChecks {
    public static final int MAX_ORDER_LINES = 200;
    private ShopQuantityChecks() { }

    public static int positive(Integer quantity) {
        if (quantity == null || quantity <= 0) Asserts.fail("商品数量必须为正整数");
        return quantity;
    }

    public static int add(int left, int right) {
        if (left < 0 || right < 0 || (long) left + right > Integer.MAX_VALUE) {
            Asserts.fail("商品数量超出系统可处理范围，请拆分处理");
        }
        return left + right;
    }

    public static void order(ShopOrderSubmitDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) Asserts.fail("订单商品不能为空");
        if (dto.getItems().size() > MAX_ORDER_LINES) Asserts.fail("单次订单最多200项商品，请拆分下单");
        int total = 0;
        for (ShopOrderItemDTO item : dto.getItems()) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0
                    || (item.getSkuId() != null && item.getSkuId() <= 0)) Asserts.fail("订单商品信息不正确");
            total = add(total, positive(item.getQuantity()));
        }
    }

    public static Map<Long, Integer> refundSelection(List<ShopAfterSaleItemDTO> items) {
        if (items == null || items.isEmpty()) Asserts.fail("请选择实际退回的商品和数量");
        Map<Long, Integer> selected = new LinkedHashMap<>();
        int total = 0;
        for (ShopAfterSaleItemDTO item : items) {
            if (item == null || item.getOrderItemId() == null || item.getOrderItemId() <= 0) Asserts.fail("售后商品信息不正确");
            int quantity = positive(item.getQuantity());
            total = add(total, quantity);
            selected.merge(item.getOrderItemId(), quantity, ShopQuantityChecks::add);
        }
        return selected;
    }

    public static int remaining(Integer original, int reserved) {
        positive(original);
        if (reserved < 0 || reserved > original) Asserts.fail("历史售后数量异常，请联系平台核查");
        return original - reserved;
    }

    public static int refundLines(List<DmsShopAfterSaleItem> items) {
        if (items == null || items.isEmpty()) Asserts.fail("售后商品明细为空");
        int total = 0;
        for (DmsShopAfterSaleItem item : items) {
            if (item == null || item.getProductId() == null) Asserts.fail("售后商品明细异常");
            total = add(total, positive(item.getRefundQuantity()));
        }
        return total;
    }
}
