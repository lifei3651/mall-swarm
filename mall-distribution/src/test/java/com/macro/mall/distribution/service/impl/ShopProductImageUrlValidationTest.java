package com.macro.mall.distribution.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopProductImageUrlValidationTest {

    @Test
    void rejectsExecutableAndInsecureProductImageSchemes() {
        assertThrows(RuntimeException.class,
                () -> ShopServiceImpl.normalizeProductImageUrl("javascript:alert(1)", "商品主图"));
        assertThrows(RuntimeException.class,
                () -> ShopServiceImpl.normalizeProductImageUrl("data:image/svg+xml,<svg/>", "商品主图"));
        assertThrows(RuntimeException.class,
                () -> ShopServiceImpl.normalizeProductImageUrl("http://example.com/product.png", "商品主图"));
        assertThrows(RuntimeException.class,
                () -> ShopServiceImpl.normalizeProductImageUrl("//example.com/product.png", "商品主图"));
    }

    @Test
    void acceptsHttpsAndSameSiteUploadedProductImages() {
        assertEquals("https://cdn.example.com/product.png",
                ShopServiceImpl.normalizeProductImageUrl(" https://cdn.example.com/product.png ", "商品主图"));
        assertEquals("/api/shop/media/images/abc123.webp",
                ShopServiceImpl.normalizeProductImageUrl("/api/shop/media/images/abc123.webp", "商品主图"));
    }
}
