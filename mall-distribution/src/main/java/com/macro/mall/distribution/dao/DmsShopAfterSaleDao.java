package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopAfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopAfterSaleDao {

    DmsShopAfterSale selectById(@Param("id") Long id);

    DmsShopAfterSale selectByIdForUpdate(@Param("id") Long id);

    DmsShopAfterSale selectOpenByOrderId(@Param("orderId") Long orderId);

    List<DmsShopAfterSale> selectByOrderId(@Param("orderId") Long orderId);

    List<DmsShopAfterSale> selectByMemberId(@Param("memberId") Long memberId);

    List<String> selectProofReferences(@Param("tenantId") Long tenantId,
                                       @Param("memberId") Long memberId,
                                       @Param("merchantId") Long merchantId);

    List<DmsShopAfterSale> selectList(@Param("tenantId") Long tenantId,
                                      @Param("keyword") String keyword,
                                      @Param("status") Integer status,
                                      @Param("merchantId") Long merchantId);

    default List<DmsShopAfterSale> selectList(String keyword, Integer status) {
        return selectList(1L, keyword, status, null);
    }

    int insert(DmsShopAfterSale afterSale);

    int updateAudit(DmsShopAfterSale afterSale);

    int updateReturnShipment(DmsShopAfterSale afterSale);

    int updateReturnReceived(DmsShopAfterSale afterSale);

    int markRefundCompleted(@Param("id") Long id);
}
