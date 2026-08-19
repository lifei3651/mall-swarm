package com.macro.mall.distribution.dao;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.entity.DmsOrderFinance;
import com.macro.mall.distribution.vo.FinanceSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOrderFinanceDao {

    DmsOrderFinance selectByOrderIdScoped(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    default DmsOrderFinance selectByOrderId(Long orderId) {
        return selectByOrderIdScoped(TenantContext.getTenantId(), orderId);
    }

    DmsOrderFinance selectByOrderNoScoped(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);

    default DmsOrderFinance selectByOrderNo(String orderNo) {
        return selectByOrderNoScoped(TenantContext.getTenantId(), orderNo);
    }

    int insertScoped(@Param("tenantId") Long tenantId, @Param("finance") DmsOrderFinance finance);

    default int insert(DmsOrderFinance finance) {
        return insertScoped(TenantContext.getTenantId(), finance);
    }

    int updateScoped(@Param("tenantId") Long tenantId, @Param("finance") DmsOrderFinance finance);

    default int update(DmsOrderFinance finance) {
        return updateScoped(TenantContext.getTenantId(), finance);
    }

    FinanceSummaryVO selectSummaryScoped(@Param("tenantId") Long tenantId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    default FinanceSummaryVO selectSummary(LocalDateTime startTime, LocalDateTime endTime) {
        return selectSummaryScoped(TenantContext.getTenantId(), startTime, endTime);
    }

    List<com.macro.mall.distribution.vo.FinanceDailySummaryVO> selectDailySummaryScoped(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    default List<com.macro.mall.distribution.vo.FinanceDailySummaryVO> selectDailySummary(
            LocalDateTime startTime, LocalDateTime endTime) {
        return selectDailySummaryScoped(TenantContext.getTenantId(), startTime, endTime);
    }
}
