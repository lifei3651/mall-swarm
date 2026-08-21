package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsCommissionSettlementItemDao {
    List<DmsCommissionSettlementItem> selectByBatchIdScoped(@Param("tenantId") Long tenantId, @Param("batchId") Long batchId);
    default List<DmsCommissionSettlementItem> selectByBatchId(Long batchId) {
        return selectByBatchIdScoped(TenantContext.getTenantId(), batchId);
    }
    int insertBatchScoped(@Param("tenantId") Long tenantId, @Param("items") List<DmsCommissionSettlementItem> items);
    default int insertBatch(List<DmsCommissionSettlementItem> items) {
        Long tenantId = TenantContext.getTenantId();
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("佣金结算明细不能为空");
        for (DmsCommissionSettlementItem item : items) {
            if (item == null) throw new IllegalArgumentException("佣金结算明细不能为空");
            if (item.getTenantId() == null) item.setTenantId(tenantId);
            if (!tenantId.equals(item.getTenantId())) throw new IllegalArgumentException("不能写入其他租户的佣金结算明细");
        }
        return insertBatchScoped(tenantId, items);
    }
    int updateStatusScoped(@Param("tenantId") Long tenantId, @Param("id") Long id,
                           @Param("status") Integer status, @Param("skipReason") String skipReason);
    default int updateStatus(Long id, Integer status, String skipReason) {
        return updateStatusScoped(TenantContext.getTenantId(), id, status, skipReason);
    }
}
