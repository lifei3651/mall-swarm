package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.entity.DmsShopOrder;
import java.util.List;

public interface ErpIntegrationService {
    List<DmsErpIntegration> listIntegrations(Long tenantId);
    DmsErpIntegration saveIntegration(DmsErpIntegration integration);
    List<DmsErpSyncTask> listTasks(Long integrationId, Integer status);
    void queueOrderPush(DmsShopOrder order);
    boolean retryTask(Long taskId);
    int retryPendingTasks(int limit);
    boolean receiveShipment(ErpShipmentCallbackDTO callback);
}
