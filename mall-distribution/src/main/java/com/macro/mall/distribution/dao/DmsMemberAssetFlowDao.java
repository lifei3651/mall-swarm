package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMemberAssetFlow;
import com.macro.mall.distribution.vo.BalanceFlowVO;
import com.macro.mall.distribution.vo.BalanceFlowSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface DmsMemberAssetFlowDao {

    DmsMemberAssetFlow selectByFlowNo(@Param("flowNo") String flowNo);

    List<DmsMemberAssetFlow> selectByAgentId(@Param("agentId") Long agentId,
                                             @Param("assetCode") String assetCode);

    List<DmsMemberAssetFlow> selectByUserId(@Param("userId") Long userId,
                                            @Param("assetCode") String assetCode);

    List<BalanceFlowVO> selectBalanceFlowList(@Param("keyword") String keyword,
                                               @Param("direction") String direction,
                                              @Param("sourceType") String sourceType,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    BalanceFlowSummaryVO selectBalanceFlowSummary(@Param("keyword") String keyword,
                                                  @Param("direction") String direction,
                                                  @Param("sourceType") String sourceType,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /** Assets actually issued when a commission record was settled. */
    List<DmsMemberAssetFlow> selectCommissionSettlementFlows(@Param("agentId") Long agentId,
                                                              @Param("commissionRecordId") Long commissionRecordId);

    int insert(DmsMemberAssetFlow flow);
}
