package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dao.DmsFlashSaleActivityDao;
import com.macro.mall.distribution.dao.DmsFlashSaleReservationDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.FlashSaleStockGate;
import com.macro.mall.distribution.service.ShopBusinessModeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashSaleOrderModeLockTest {

    @Mock private DmsShopMemberDao memberDao;
    @Mock private DmsFlashSaleActivityDao activityDao;
    @Mock private DmsFlashSaleReservationDao reservationDao;
    @Mock private ShopBusinessModeService businessModeService;
    @Mock private FlashSaleStockGate stockGate;
    @InjectMocks private FlashSaleServiceImpl service;

    @Test
    void locksMemberThenTenantBeforeReadingActivityOrReservingStock() {
        DmsShopMember member = member();
        when(memberDao.selectByIdForUpdate(7L)).thenReturn(member);

        assertThrows(ApiException.class, () -> service.submit(9L, order(), member));

        InOrder order = inOrder(memberDao, businessModeService, activityDao);
        order.verify(memberDao).selectByIdForUpdate(7L);
        order.verify(businessModeService).requireEnabledForOrder(1L, ShopBusinessType.FLASH_SALE, member);
        order.verify(activityDao).selectById(9L);
        verifyNoInteractions(stockGate, reservationDao);
    }

    @Test
    void closedModuleStopsBeforeActivityAndStockSideEffects() {
        DmsShopMember member = member();
        when(memberDao.selectByIdForUpdate(7L)).thenReturn(member);
        doThrow(new IllegalStateException("closed"))
                .when(businessModeService).requireEnabledForOrder(1L, ShopBusinessType.FLASH_SALE, member);

        assertThrows(IllegalStateException.class, () -> service.submit(9L, order(), member));

        verify(memberDao).selectByIdForUpdate(7L);
        verifyNoInteractions(activityDao, stockGate, reservationDao);
    }

    private DmsShopMember member() {
        DmsShopMember member = new DmsShopMember();
        member.setId(7L);
        member.setUserId(8L);
        member.setStatus(1);
        return member;
    }

    private ShopOrderSubmitDTO order() {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(10L);
        item.setQuantity(1);
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setItems(List.of(item));
        return dto;
    }
}
