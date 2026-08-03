package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopMember;

import java.util.List;

public interface ShopAfterSaleService {

    DmsShopAfterSale apply(DmsShopMember member, ShopAfterSaleApplyDTO dto);

    List<DmsShopAfterSale> listByMember(DmsShopMember member);

    List<DmsShopAfterSale> listAdmin(String keyword, Integer status);

    DmsShopAfterSale audit(Long id, ShopAfterSaleAuditDTO dto);
}
