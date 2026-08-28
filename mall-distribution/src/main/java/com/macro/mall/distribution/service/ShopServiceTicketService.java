package com.macro.mall.distribution.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.distribution.dto.ShopServiceTicketAdminActionDTO;
import com.macro.mall.distribution.dto.ShopServiceTicketCreateDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopServiceTicket;
import com.macro.mall.distribution.vo.ShopServiceTicketDetailVO;

public interface ShopServiceTicketService {
    CommonPage<DmsShopServiceTicket> listMember(DmsShopMember member, String status, int pageNum, int pageSize);
    ShopServiceTicketDetailVO create(DmsShopMember member, ShopServiceTicketCreateDTO input);
    ShopServiceTicketDetailVO memberDetail(DmsShopMember member, Long id);
    ShopServiceTicketDetailVO memberReply(DmsShopMember member, Long id, String content);
    ShopServiceTicketDetailVO memberClose(DmsShopMember member, Long id);
    CommonPage<DmsShopServiceTicket> listAdmin(DmsAdminUser admin, String keyword, String status, String type,
                                               int pageNum, int pageSize);
    ShopServiceTicketDetailVO adminDetail(DmsAdminUser admin, Long id);
    ShopServiceTicketDetailVO adminReply(DmsAdminUser admin, Long id, ShopServiceTicketAdminActionDTO input);
}
