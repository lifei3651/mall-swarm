package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopServiceAddress;

import java.util.List;

public interface ShopServiceAddressService {

    List<DmsShopServiceAddress> list(Long tenantId, Integer addressType, Integer status);

    DmsShopServiceAddress save(DmsShopServiceAddress address);

    boolean updateStatus(Long id, Long tenantId, Integer status);

    DmsShopServiceAddress getDefault(Long tenantId, Integer addressType);
}
