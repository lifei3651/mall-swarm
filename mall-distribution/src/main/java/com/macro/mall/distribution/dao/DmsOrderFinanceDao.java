package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderFinance;
import com.macro.mall.distribution.vo.FinanceSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOrderFinanceDao {

    DmsOrderFinance selectByOrderId(@Param("orderId") Long orderId);

    DmsOrderFinance selectByOrderNo(@Param("orderNo") String orderNo);

    int insert(DmsOrderFinance finance);

    int update(DmsOrderFinance finance);

    FinanceSummaryVO selectSummary(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    List<com.macro.mall.distribution.vo.FinanceDailySummaryVO> selectDailySummary(@Param("startTime") LocalDateTime startTime,
                                                                                  @Param("endTime") LocalDateTime endTime);
}
