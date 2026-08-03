package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.CommissionSettlementBatchCreateDTO;
import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import java.util.List;

public interface CommissionSettlementService {
    DmsCommissionSettlementBatch createBatch(CommissionSettlementBatchCreateDTO dto);
    DmsCommissionSettlementBatch executeBatch(Long id);
    List<DmsCommissionSettlementBatch> listBatches(Integer status);
    List<DmsCommissionSettlementItem> listItems(Long batchId);
    int settleEligibleAfterCoolingOff(int limit);
}
