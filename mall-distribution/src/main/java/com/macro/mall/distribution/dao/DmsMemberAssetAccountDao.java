package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMemberAssetAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DmsMemberAssetAccountDao {

    DmsMemberAssetAccount selectByAgentIdAndAssetCode(@Param("agentId") Long agentId,
                                                      @Param("assetCode") String assetCode);

    List<DmsMemberAssetAccount> selectByAgentId(@Param("agentId") Long agentId);

    int insert(DmsMemberAssetAccount account);

    int addBalance(@Param("agentId") Long agentId,
                   @Param("assetCode") String assetCode,
                   @Param("amount") BigDecimal amount);

    int subtractBalance(@Param("agentId") Long agentId,
                        @Param("assetCode") String assetCode,
                        @Param("amount") BigDecimal amount,
                        @Param("allowNegative") Integer allowNegative);
}
