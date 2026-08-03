package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopAddressDTO;
import com.macro.mall.distribution.entity.DmsShopAddress;
import com.macro.mall.distribution.entity.DmsShopMember;

import java.util.List;

public interface ShopAddressService {

    List<DmsShopAddress> list(DmsShopMember member);

    DmsShopAddress save(DmsShopMember member, ShopAddressDTO dto);

    boolean delete(DmsShopMember member, Long id);
}
