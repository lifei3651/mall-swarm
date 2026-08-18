package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsMerchantLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DmsMerchantLedgerDao {
    int insert(DmsMerchantLedger ledger);
    List<DmsMerchantLedger> selectList(@Param("tenantId") Long tenantId,
                                       @Param("merchantId") Long merchantId,
                                       @Param("bizType") String bizType);
}
