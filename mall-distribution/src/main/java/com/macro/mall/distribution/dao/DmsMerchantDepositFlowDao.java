package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantDepositFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsMerchantDepositFlowDao {
    DmsMerchantDepositFlow selectByOperationNo(@Param("operationNo") String operationNo);
    List<DmsMerchantDepositFlow> selectList(@Param("tenantId") Long tenantId,
                                            @Param("merchantId") Long merchantId);
    int insert(DmsMerchantDepositFlow flow);
}
