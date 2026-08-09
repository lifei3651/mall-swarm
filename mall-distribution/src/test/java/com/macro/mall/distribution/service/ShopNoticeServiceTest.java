package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopNoticeDao;
import com.macro.mall.distribution.entity.DmsShopNotice;
import com.macro.mall.distribution.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopNoticeServiceTest {

    @Mock private DmsShopNoticeDao noticeDao;
    @InjectMocks private ShopServiceImpl shopService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void adminCanDeleteNoticeBelongingToCurrentTenant() {
        TenantContext.setTenantId(1L);
        DmsShopNotice notice = new DmsShopNotice();
        notice.setId(9L);
        notice.setTenantId(1L);
        when(noticeDao.selectById(9L)).thenReturn(notice);
        when(noticeDao.deleteById(9L)).thenReturn(1);

        assertTrue(shopService.deleteNotice(9L));

        verify(noticeDao).deleteById(9L);
    }
}
