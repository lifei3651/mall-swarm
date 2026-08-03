package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsErpIntegrationDao;
import com.macro.mall.distribution.dao.DmsErpSyncTaskDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dto.ErpShipmentCallbackDTO;
import com.macro.mall.distribution.entity.DmsErpIntegration;
import com.macro.mall.distribution.entity.DmsErpSyncTask;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.erp.ErpAdapter;
import com.macro.mall.distribution.service.ErpIntegrationService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.OrderShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ErpIntegrationServiceImpl implements ErpIntegrationService {
    private static final String BIZ_ORDER_PUSH = "ORDER_PUSH";
    private final DmsErpIntegrationDao integrationDao;
    private final DmsErpSyncTaskDao taskDao;
    private final DmsShopOrderDao orderDao;
    private final List<ErpAdapter> adapters;
    private final OperationLogService operationLogService;
    private final OrderShipmentService orderShipmentService;

    @Override public List<DmsErpIntegration> listIntegrations(Long tenantId) { return integrationDao.selectList(tenantId == null ? 1L : tenantId); }

    @Override @Transactional(rollbackFor = Exception.class)
    public DmsErpIntegration saveIntegration(DmsErpIntegration integration) {
        if (integration == null || integration.getProviderCode() == null || integration.getProviderCode().isBlank()) Asserts.fail("ERP厂商不能为空");
        if (!List.of("JUSHUITAN", "WANGDIAN", "KINGDEE").contains(integration.getProviderCode())) Asserts.fail("暂仅支持聚水潭、旺店通、金蝶");
        integration.setTenantId(integration.getTenantId() == null ? 1L : integration.getTenantId());
        integration.setIntegrationName(integration.getIntegrationName() == null ? integration.getProviderCode() : integration.getIntegrationName());
        integration.setEnabled(integration.getEnabled() == null ? 0 : integration.getEnabled());
        integration.setEnvironment(integration.getEnvironment() == null ? "TEST" : integration.getEnvironment());
        validateSecureEndpoint(integration.getEndpoint());
        DmsErpIntegration existing = integrationDao.selectByTenantAndProvider(integration.getTenantId(), integration.getProviderCode());
        if (existing == null) integrationDao.insert(integration); else { integration.setId(existing.getId()); integrationDao.update(integration); }
        DmsErpIntegration result = integrationDao.selectByTenantAndProvider(integration.getTenantId(), integration.getProviderCode());
        operationLogService.log("ERP", "CONFIG_SAVE", "ERP_INTEGRATION", String.valueOf(result.getId()), null,
                "provider=" + result.getProviderCode() + ", environment=" + result.getEnvironment()
                        + ", enabled=" + result.getEnabled() + ", endpoint=" + result.getEndpoint(),
                "保存ERP配置（密钥和回调令牌不写入操作日志）");
        return result;
    }

    @Override public List<DmsErpSyncTask> listTasks(Long integrationId, Integer status) { return taskDao.selectList(integrationId, status); }

    @Override @Transactional(rollbackFor = Exception.class)
    public void queueOrderPush(DmsShopOrder order) {
        if (order == null || order.getId() == null) return;
        for (DmsErpIntegration integration : integrationDao.selectEnabled(order.getTenantId() == null ? 1L : order.getTenantId())) {
            if (taskDao.selectByUnique(integration.getId(), BIZ_ORDER_PUSH, String.valueOf(order.getId())) != null) continue;
            DmsErpSyncTask task = new DmsErpSyncTask();
            task.setTaskNo("ERP" + IdUtil.getSnowflakeNextIdStr()); task.setIntegrationId(integration.getId());
            task.setTenantId(integration.getTenantId()); task.setProviderCode(integration.getProviderCode()); task.setBizType(BIZ_ORDER_PUSH);
            task.setBizId(String.valueOf(order.getId())); task.setStatus(0); task.setRetryCount(0); task.setRequestSummary("商城订单=" + order.getOrderNo());
            taskDao.insert(task);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public boolean retryTask(Long taskId) {
        DmsErpSyncTask task = taskDao.selectById(taskId); if (task == null) Asserts.fail("ERP任务不存在");
        DmsErpIntegration integration = integrationDao.selectById(task.getIntegrationId());
        DmsShopOrder order = orderDao.selectById(Long.valueOf(task.getBizId()));
        if (integration == null || order == null) { fail(task, "ERP配置或商城订单不存在"); return false; }
        Map<String, ErpAdapter> adapterMap = adapters.stream().collect(Collectors.toMap(ErpAdapter::providerCode, item -> item));
        ErpAdapter adapter = adapterMap.get(integration.getProviderCode());
        if (adapter == null) { fail(task, "未找到ERP适配器：" + integration.getProviderCode()); return false; }
        ErpAdapter.ErpPushResult result = adapter.pushOrder(integration, order);
        if (result.success()) { taskDao.markSuccess(task.getId(), result.message()); operationLogService.log("ERP", "ORDER_PUSH_SUCCESS", "ERP_SYNC_TASK", String.valueOf(task.getId()), null, result.message(), "ERP订单推送成功"); return true; }
        fail(task, result.message()); return false;
    }

    @Override public int retryPendingTasks(int limit) { int count = 0; for (DmsErpSyncTask task : taskDao.selectRetryable(LocalDateTime.now(), limit)) { retryTask(task.getId()); count++; } return count; }

    @Override @Transactional(rollbackFor = Exception.class)
    public boolean receiveShipment(ErpShipmentCallbackDTO callback) {
        if (callback == null || callback.getProviderCode() == null || callback.getOrderNo() == null || callback.getDeliveryNo() == null) Asserts.fail("ERP发货回传参数不完整");
        DmsErpIntegration integration = integrationDao.selectByTenantAndProvider(1L, callback.getProviderCode());
        if (integration == null || !Integer.valueOf(1).equals(integration.getEnabled())) Asserts.fail("ERP集成未启用");
        if (!secureEquals(integration.getCallbackToken(), callback.getToken())) Asserts.fail("ERP回调鉴权失败");
        return orderShipmentService.shipErpOrder(callback.getOrderNo(), callback.getDeliveryCompany(),
                callback.getDeliveryNo(), callback.getShipmentQuantity(), callback.getProviderCode());
    }

    private void validateSecureEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return;
        try {
            URI uri = URI.create(endpoint.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                Asserts.fail("ERP接口地址必须使用有效的HTTPS地址");
            }
        } catch (IllegalArgumentException ex) {
            Asserts.fail("ERP接口地址格式不正确");
        }
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private void fail(DmsErpSyncTask task, String error) { int retry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1; taskDao.markFailure(task.getId(), retry, LocalDateTime.now().plusMinutes(Math.min(60, retry * 5L)), error); operationLogService.log("ERP", "ORDER_PUSH_FAIL", "ERP_SYNC_TASK", String.valueOf(task.getId()), null, error, "ERP订单推送失败，等待重试"); }
}
