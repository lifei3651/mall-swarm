package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.FlashSaleActivitySaveDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.FlashSaleActivityVO;
import com.macro.mall.distribution.vo.ShopOrderVO;

import java.util.List;

public interface FlashSaleService {
    List<FlashSaleActivityVO> listFront();
    List<FlashSaleActivityVO> listAdmin(Integer status);
    DmsFlashSaleActivity save(Long id, FlashSaleActivitySaveDTO dto);
    boolean updateStatus(Long id, Integer status);
    ShopOrderVO submit(Long activityId, ShopOrderSubmitDTO dto, DmsShopMember member);
}
