package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsShopTrade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DmsShopTradeDao {
    DmsShopTrade selectByIdScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopTrade selectById(Long id) { return selectByIdScoped(TenantContext.getTenantId(), id); }

    DmsShopTrade selectByIdForUpdateScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default DmsShopTrade selectByIdForUpdate(Long id) { return selectByIdForUpdateScoped(TenantContext.getTenantId(), id); }

    DmsShopTrade selectByTradeNoScoped(@Param("tenantId") Long tenantId, @Param("tradeNo") String tradeNo);
    default DmsShopTrade selectByTradeNo(String tradeNo) { return selectByTradeNoScoped(TenantContext.getTenantId(), tradeNo); }

    DmsShopTrade selectByTradeNoForUpdateScoped(@Param("tenantId") Long tenantId, @Param("tradeNo") String tradeNo);
    default DmsShopTrade selectByTradeNoForUpdate(String tradeNo) {
        return selectByTradeNoForUpdateScoped(TenantContext.getTenantId(), tradeNo);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("trade") DmsShopTrade trade);
    default int insert(DmsShopTrade trade) {
        Long tenantId = TenantContext.getTenantId();
        if (trade == null) throw new IllegalArgumentException("交易父单不能为空");
        if (trade.getTenantId() == null) trade.setTenantId(tenantId);
        if (!tenantId.equals(trade.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的交易父单");
        return insertScoped(tenantId, trade);
    }

    int markPaidScoped(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("payType") String payType);
    default int markPaid(Long id, String payType) { return markPaidScoped(TenantContext.getTenantId(), id, payType); }

    int closePendingScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int closePending(Long id) { return closePendingScoped(TenantContext.getTenantId(), id); }

    int markLateRefundedScoped(@Param("tenantId") Long tenantId, @Param("id") Long id);
    default int markLateRefunded(Long id) { return markLateRefundedScoped(TenantContext.getTenantId(), id); }
}
