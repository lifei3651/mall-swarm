package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsBonusCalculationTask;

import java.math.BigDecimal;
import java.util.List;

public interface BonusCalculationTaskService {

    DmsBonusCalculationTask enqueue(Long tenantId,
                                    Long ruleVersionId,
                                    Long orderId,
                                    String orderNo,
                                    BigDecimal orderAmount,
                                    Long orderUserId,
                                    String orderUserName);

    List<DmsBonusCalculationTask> listTasks(Integer status, Long orderId);

    int processPendingTasks(Integer limit);

    boolean processTask(Long taskId);
}
