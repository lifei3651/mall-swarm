package com.macro.mall.distribution.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShopPublicViewSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ordinaryProductResponseOmitsInternalBusinessAndAddressFields() {
        DmsShopProduct product = new DmsShopProduct();
        product.setId(1L);
        product.setProductName("公开商品");
        product.setSalePrice(new BigDecimal("99.00"));
        product.setStock(8);
        product.setCostAmount(new BigDecimal("20.00"));
        product.setSettlementDelayDaysOverride(30);
        product.setBvValue(new BigDecimal("30.00"));
        product.setSafetyStock(2);
        product.setDeliveryAddress("内部仓库详细地址");
        product.setDeliveryProvince("广东省");
        product.setDeliveryCity("深圳市");
        product.setDeliveryDistrict("南山区");
        product.setShippingAddressId(11L);
        product.setReturnAddressId(12L);
        product.setFreightTemplateId(13L);
        product.setRepurchaseSaleEnabled(1);
        product.setRepurchasePrice(new BigDecimal("88.00"));
        product.setRepurchasePv(new BigDecimal("18.00"));
        product.setRepurchasePurchaseLimit(3);
        product.setMerchantReviewStatus("APPROVED");
        product.setMerchantReviewVersion(2);

        JsonNode json = objectMapper.valueToTree(ShopPublicViewSanitizer.product(product, false));

        for (String field : new String[]{"costAmount", "bvValue", "safetyStock", "deliveryAddress",
                "deliveryProvince", "deliveryCity", "deliveryDistrict", "shippingAddressId",
                "returnAddressId", "freightTemplateId", "repurchaseSaleEnabled", "repurchasePrice",
                "repurchasePv", "repurchasePurchaseLimit", "merchantReviewStatus", "merchantReviewVersion",
                "settlementDelayDaysOverride"}) {
            assertFalse(json.has(field), field + " must not be serialized in the public response");
        }
        assertEquals("公开商品", json.get("productName").asText());
        assertEquals(0, json.get("salePrice").decimalValue().compareTo(new BigDecimal("99.00")));
        assertEquals(8, json.get("stock").asInt());
    }

    @Test
    void publicSkuResponseOmitsInternalFieldsButKeepsSellableData() {
        DmsShopSku sku = new DmsShopSku();
        sku.setId(2L);
        sku.setSkuName("公开规格");
        sku.setSalePrice(new BigDecimal("59.00"));
        sku.setStock(6);
        sku.setCostAmount(new BigDecimal("10.00"));
        sku.setBvValue(new BigDecimal("15.00"));
        sku.setSafetyStock(1);
        sku.setRepurchasePrice(new BigDecimal("49.00"));
        sku.setRepurchasePv(new BigDecimal("9.00"));

        JsonNode json = objectMapper.valueToTree(ShopPublicViewSanitizer.sku(sku, false));

        for (String field : new String[]{"costAmount", "bvValue", "safetyStock", "repurchasePrice", "repurchasePv"}) {
            assertFalse(json.has(field), field + " must not be serialized in the public response");
        }
        assertEquals("公开规格", json.get("skuName").asText());
        assertEquals(0, json.get("salePrice").decimalValue().compareTo(new BigDecimal("59.00")));
        assertEquals(6, json.get("stock").asInt());
    }

    @Test
    void publicOrderResponseOmitsMerchantSettlementDelaySnapshot() {
        DmsShopOrderItem item = new DmsShopOrderItem();
        item.setProductName("公开订单商品");
        item.setPrice(new BigDecimal("99.00"));
        item.setSettlementDelayDays(30);
        ShopOrderVO order = new ShopOrderVO();
        order.setItems(List.of(item));

        JsonNode json = objectMapper.valueToTree(ShopPublicViewSanitizer.order(order));

        assertFalse(json.path("items").get(0).has("settlementDelayDays"));
        assertEquals("公开订单商品", json.path("items").get(0).path("productName").asText());
    }
}
