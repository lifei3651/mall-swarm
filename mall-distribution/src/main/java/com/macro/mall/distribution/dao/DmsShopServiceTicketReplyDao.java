package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopServiceTicketReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopServiceTicketReplyDao {
    List<DmsShopServiceTicketReply> selectByTicketId(@Param("tenantId") Long tenantId,
                                                     @Param("ticketId") Long ticketId);
    int insert(DmsShopServiceTicketReply reply);
}
