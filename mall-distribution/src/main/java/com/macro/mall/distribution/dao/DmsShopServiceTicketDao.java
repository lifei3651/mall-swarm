package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopServiceTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsShopServiceTicketDao {
    DmsShopServiceTicket selectOwned(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                                     @Param("id") Long id);
    DmsShopServiceTicket selectOwnedForUpdate(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                                              @Param("id") Long id);
    DmsShopServiceTicket selectAdmin(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId,
                                     @Param("id") Long id);
    DmsShopServiceTicket selectAdminForUpdate(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId,
                                              @Param("id") Long id);
    List<DmsShopServiceTicket> selectOwnedList(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                                               @Param("status") String status);
    List<DmsShopServiceTicket> selectAdminList(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId,
                                               @Param("keyword") String keyword, @Param("status") String status,
                                               @Param("type") String type);
    int countActiveOwned(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);
    int countActiveByAfterSale(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                               @Param("afterSaleId") Long afterSaleId);
    int insert(DmsShopServiceTicket ticket);
    int updateAfterMemberReply(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                               @Param("id") Long id, @Param("replyTime") LocalDateTime replyTime);
    int updateAfterAdminReply(@Param("tenantId") Long tenantId, @Param("merchantId") Long merchantId,
                              @Param("id") Long id, @Param("status") String status,
                              @Param("adminId") Long adminId, @Param("adminName") String adminName,
                              @Param("replyTime") LocalDateTime replyTime);
    int closeOwned(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId,
                   @Param("id") Long id, @Param("closeTime") LocalDateTime closeTime);
}
