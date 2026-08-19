package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface DmsShopAfterSaleDao {

    DmsShopAfterSale selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopAfterSale selectById(Long id) { return selectByIdScoped(TenantContext.getTenantId(), id); }

    DmsShopAfterSale selectByIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopAfterSale selectByIdForUpdate(Long id) { return selectByIdForUpdateScoped(TenantContext.getTenantId(), id); }

    DmsShopAfterSale selectOpenByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default DmsShopAfterSale selectOpenByOrderId(Long orderId) { return selectOpenByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    List<DmsShopAfterSale> selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default List<DmsShopAfterSale> selectByOrderId(Long orderId) { return selectByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    List<DmsShopAfterSale> selectByMemberIdScoped(@Param("tenantId") Long tenantId, @Param("memberId") Long memberId);
    default List<DmsShopAfterSale> selectByMemberId(Long memberId) { return selectByMemberIdScoped(TenantContext.getTenantId(), memberId); }

    List<String> selectProofReferences(@Param("tenantId") Long tenantId,
                                       @Param("memberId") Long memberId,
                                       @Param("merchantId") Long merchantId);

    List<DmsShopAfterSale> selectList(@Param("tenantId") Long tenantId,
                                      @Param("keyword") String keyword,
                                      @Param("status") Integer status,
                                      @Param("merchantId") Long merchantId);

    default List<DmsShopAfterSale> selectList(String keyword, Integer status) {
        return selectList(TenantContext.getTenantId(), keyword, status, null);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("afterSale") DmsShopAfterSale afterSale);
    default int insert(DmsShopAfterSale afterSale) { return insertScoped(TenantContext.getTenantId(), afterSale); }

    int updateAuditScoped(@Param("tenantId") Long tenantId, @Param("afterSale") DmsShopAfterSale afterSale);
    default int updateAudit(DmsShopAfterSale afterSale) { return updateAuditScoped(TenantContext.getTenantId(), afterSale); }

    int updateReturnShipmentScoped(@Param("tenantId") Long tenantId, @Param("afterSale") DmsShopAfterSale afterSale);
    default int updateReturnShipment(DmsShopAfterSale afterSale) { return updateReturnShipmentScoped(TenantContext.getTenantId(), afterSale); }

    int updateReturnReceivedScoped(@Param("tenantId") Long tenantId, @Param("afterSale") DmsShopAfterSale afterSale);
    default int updateReturnReceived(DmsShopAfterSale afterSale) { return updateReturnReceivedScoped(TenantContext.getTenantId(), afterSale); }

    int markRefundCompletedScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int markRefundCompleted(Long id) { return markRefundCompletedScoped(TenantContext.getTenantId(), id); }

    List<Long> selectExpiredWaitingReturnIdsScoped(@Param("tenantId") Long tenantId,
                                                   @Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);
    default List<Long> selectExpiredWaitingReturnIds(LocalDateTime cutoff, int limit) {
        return selectExpiredWaitingReturnIdsScoped(TenantContext.getTenantId(), cutoff, limit);
    }
}
