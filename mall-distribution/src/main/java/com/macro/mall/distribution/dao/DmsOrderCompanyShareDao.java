package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsOrderCompanyShare;
import com.macro.mall.distribution.vo.CompanyShareSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOrderCompanyShareDao {

    List<DmsOrderCompanyShare> selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default List<DmsOrderCompanyShare> selectByOrderId(Long orderId) {
        return selectByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    List<CompanyShareSummaryVO> selectSummaryScoped(@Param("tenantId") Long tenantId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);
    default List<CompanyShareSummaryVO> selectSummary(LocalDateTime startTime, LocalDateTime endTime) {
        return selectSummaryScoped(TenantContext.getTenantId(), startTime, endTime);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("share") DmsOrderCompanyShare share);
    default int insert(DmsOrderCompanyShare share) { return insertScoped(TenantContext.getTenantId(), share); }

    int deleteByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    default int deleteByOrderId(Long orderId) { return deleteByOrderIdScoped(TenantContext.getTenantId(), orderId); }
}
