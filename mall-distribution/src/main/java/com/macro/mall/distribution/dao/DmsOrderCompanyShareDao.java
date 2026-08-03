package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsOrderCompanyShare;
import com.macro.mall.distribution.vo.CompanyShareSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DmsOrderCompanyShareDao {

    List<DmsOrderCompanyShare> selectByOrderId(@Param("orderId") Long orderId);

    List<CompanyShareSummaryVO> selectSummary(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    int insert(DmsOrderCompanyShare share);

    int deleteByOrderId(@Param("orderId") Long orderId);
}
