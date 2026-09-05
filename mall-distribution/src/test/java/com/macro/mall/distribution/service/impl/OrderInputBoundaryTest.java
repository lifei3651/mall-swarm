package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderInputBoundaryTest {
    @Test
    void quoteRejectsOversizedInputBeforeTouchingAnyDependency() {
        ShopServiceImpl service = mock(ShopServiceImpl.class, CALLS_REAL_METHODS);
        assertThrows(ApiException.class, () -> service.quoteFreight(request(201), null));
    }

    @Test
    void submitRejectsOversizedInputBeforeMemberLock() {
        ShopServiceImpl service = mock(ShopServiceImpl.class, CALLS_REAL_METHODS);
        DmsShopMember member = new DmsShopMember(); member.setId(1L);
        assertThrows(ApiException.class, () -> service.submitOrder(request(201), member));
    }

    @Test
    void flashRejectsOversizedInputBeforeActivityLookup() {
        FlashSaleServiceImpl service = mock(FlashSaleServiceImpl.class, CALLS_REAL_METHODS);
        DmsShopMember member = new DmsShopMember(); member.setId(1L); member.setUserId(1L);
        assertThrows(ApiException.class, () -> service.submit(1L, request(201), member));
    }

    @Test
    void quoteRejectsAggregateOverflowNullAndNonpositiveItems() {
        ShopServiceImpl service = mock(ShopServiceImpl.class, CALLS_REAL_METHODS);
        ShopOrderSubmitDTO dto = request(2);
        dto.getItems().get(0).setQuantity(Integer.MAX_VALUE);
        assertThrows(ApiException.class, () -> service.quoteFreight(dto, null));
        dto.getItems().set(0, null);
        assertThrows(ApiException.class, () -> service.quoteFreight(dto, null));
        dto.setItems(new ArrayList<>(List.of(item(0))));
        assertThrows(ApiException.class, () -> service.quoteFreight(dto, null));
    }

    static ShopOrderSubmitDTO request(int count) {
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setItems(new ArrayList<>());
        for (int i = 0; i < count; i++) dto.getItems().add(item(1));
        return dto;
    }
    static ShopOrderItemDTO item(int quantity) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L); item.setQuantity(quantity); return item;
    }
}
