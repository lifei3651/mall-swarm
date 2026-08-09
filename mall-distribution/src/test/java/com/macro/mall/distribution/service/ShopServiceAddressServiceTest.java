package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsShopServiceAddressDao;
import com.macro.mall.distribution.entity.DmsShopServiceAddress;
import com.macro.mall.distribution.service.impl.ShopServiceAddressServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopServiceAddressServiceTest {

    @Test
    void rejectsLettersInContactPhoneEvenWhenApiIsCalledDirectly() {
        DmsShopServiceAddressDao dao = mock(DmsShopServiceAddressDao.class);
        ShopServiceAddressServiceImpl service = new ShopServiceAddressServiceImpl(dao);
        DmsShopServiceAddress address = validAddress();
        address.setContactPhone("电话123456");

        ApiException error = assertThrows(ApiException.class, () -> service.save(address));

        assertEquals("请填写正确的手机号或座机号码", error.getMessage());
        verifyNoInteractions(dao);
    }

    @Test
    void savesACompleteRegionAndNormalizedTelephone() {
        DmsShopServiceAddressDao dao = mock(DmsShopServiceAddressDao.class);
        when(dao.selectList(1L, 1, 1)).thenReturn(List.of());
        doAnswer(invocation -> {
            DmsShopServiceAddress value = invocation.getArgument(0);
            value.setId(8L);
            return 1;
        }).when(dao).insert(any());
        when(dao.selectById(8L)).thenAnswer(invocation -> validAddress());
        ShopServiceAddressServiceImpl service = new ShopServiceAddressServiceImpl(dao);
        DmsShopServiceAddress address = validAddress();
        address.setContactPhone(" 0731-12345678 ");

        service.save(address);

        assertEquals("0731-12345678", address.getContactPhone());
        assertEquals("湖南省", address.getProvince());
        assertEquals("长沙市", address.getCity());
        assertEquals("岳麓区", address.getDistrict());
        verify(dao).insert(address);
    }

    private DmsShopServiceAddress validAddress() {
        DmsShopServiceAddress address = new DmsShopServiceAddress();
        address.setTenantId(1L);
        address.setAddressType(1);
        address.setContactName("仓库联系人");
        address.setContactPhone("13800138000");
        address.setProvince("湖南省");
        address.setCity("长沙市");
        address.setDistrict("岳麓区");
        address.setDetailAddress("测试路1号");
        return address;
    }
}
