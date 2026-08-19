package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsFinanceRefundDao {

    List<DmsFinanceRefund> selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    default List<DmsFinanceRefund> selectByOrderId(Long orderId) {
        return selectByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    BigDecimal sumByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default BigDecimal sumByOrderId(Long orderId) { return sumByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    BigDecimal sumProductByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default BigDecimal sumProductByOrderId(Long orderId) { return sumProductByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    BigDecimal sumFreightByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default BigDecimal sumFreightByOrderId(Long orderId) { return sumFreightByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    int sumQuantityByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default int sumQuantityByOrderId(Long orderId) { return sumQuantityByOrderIdScoped(TenantContext.getTenantId(), orderId); }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("refund") DmsFinanceRefund refund);
    default int insert(DmsFinanceRefund refund) { return insertScoped(TenantContext.getTenantId(), refund); }
}
