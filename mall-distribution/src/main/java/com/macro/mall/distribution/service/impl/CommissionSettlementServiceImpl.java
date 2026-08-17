package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsCommissionSettlementBatchDao;
import com.macro.mall.distribution.dao.DmsCommissionSettlementItemDao;
import com.macro.mall.distribution.dto.CommissionSettlementBatchCreateDTO;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsCommissionSettlementBatch;
import com.macro.mall.distribution.entity.DmsCommissionSettlementItem;
import com.macro.mall.distribution.service.CommissionService;
import com.macro.mall.distribution.service.CommissionSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionSettlementServiceImpl implements CommissionSettlementService {
    private final DmsCommissionSettlementBatchDao batchDao;
    private final DmsCommissionSettlementItemDao itemDao;
    private final DmsCommissionRecordDao recordDao;
    private final CommissionService commissionService;

    @Override
    public DmsCommissionSettlementBatch createBatch(CommissionSettlementBatchCreateDTO dto) {
        Asserts.fail("月度人工结算已停用，奖金在订单达到商城售后期限且无待处理售后后自动结算");
        return null;
    }

    @Override
    public DmsCommissionSettlementBatch executeBatch(Long id) {
        Asserts.fail("月度人工结算已停用，奖金在订单达到商城售后期限且无待处理售后后自动结算");
        return null;
    }

    @Override public List<DmsCommissionSettlementBatch> listBatches(Integer status) { return batchDao.selectList(status); }
    @Override public List<DmsCommissionSettlementItem> listItems(Long batchId) { return itemDao.selectByBatchId(batchId); }

    @Override
    public int settleEligibleAfterCoolingOff(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        LocalDateTime receivedCutoff = LocalDateTime.now();
        int settled = 0;
        for (DmsCommissionRecord record : recordDao.selectEligibleForCoolingOffSettlement(receivedCutoff, safeLimit)) {
            try {
                if (commissionService.settleCommissionIfEligible(record.getId())) settled++;
            } catch (Exception ex) {
                log.error("奖金售后等待期自动结算失败: recordId={}, orderId={}", record.getId(), record.getOrderId(), ex);
            }
        }
        return settled;
    }

}
