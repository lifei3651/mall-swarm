package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class ErpIntegrationServiceImpl implements ErpIntegrationService {
    private static final String BIZ_ORDER_PUSH = "ORDER_PUSH";
    private final DmsErpIntegrationDao integrationDao;
    private final DmsErpSyncTaskDao taskDao;
    private final DmsShopOrderDao orderDao;
    private final List<ErpAdapter> adapters;
    private final OperationLogService operationLogService;
    private final OrderShipmentService orderShipmentService;

    @Value("${erp.sync.max-retries:8}")
    private int maxAutoRetries;

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
        integration.setAppSecret(blankToNull(integration.getAppSecret()));
        integration.setCallbackToken(blankToNull(integration.getCallbackToken()));
        if (integration.getCallbackToken() != null && integration.getCallbackToken().length() < 32) {
            Asserts.fail("ERP回调令牌必须使用至少32位的强随机值");
        }
        if (Integer.valueOf(1).equals(integration.getEnabled())) {
            String effectiveCallbackToken = integration.getCallbackToken() == null && existing != null
                    ? existing.getCallbackToken() : integration.getCallbackToken();
            if (effectiveCallbackToken == null || effectiveCallbackToken.length() < 32) {
                Asserts.fail("启用ERP前必须配置至少32位的强随机回调令牌");
            }
            ErpAdapter adapter = adapters.stream()
                    .filter(item -> integration.getProviderCode().equals(item.providerCode()))
                    .findFirst().orElse(null);
            if (adapter == null || !adapter.orderPushReady()) {
                Asserts.fail(integration.getProviderCode() + " 适配器尚未完成客户授权接口映射，当前只能保存为停用，不能启用自动推单");
            }
        }
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
        return executeTask(task);
    }

    private boolean executeTask(DmsErpSyncTask task) {
        DmsErpIntegration integration = integrationDao.selectById(task.getIntegrationId());
        DmsShopOrder order = orderDao.selectById(Long.valueOf(task.getBizId()));
        if (integration == null || order == null) { fail(task, "ERP配置或商城订单不存在"); return false; }
        Map<String, ErpAdapter> adapterMap = adapters.stream().collect(Collectors.toMap(ErpAdapter::providerCode, item -> item));
        ErpAdapter adapter = adapterMap.get(integration.getProviderCode());
        if (adapter == null) { fail(task, "未找到ERP适配器：" + integration.getProviderCode()); return false; }
        ErpAdapter.ErpPushResult result;
        try {
            result = adapter.pushOrder(integration, order);
        } catch (Exception ex) {
            log.error("ERP适配器调用异常: taskId={}, provider={}", task.getId(), integration.getProviderCode(), ex);
            fail(task, "ERP适配器调用异常");
            return false;
        }
        if (result.success()) { taskDao.markSuccess(task.getId(), result.message()); operationLogService.log("ERP", "ORDER_PUSH_SUCCESS", "ERP_SYNC_TASK", String.valueOf(task.getId()), null, result.message(), "ERP订单推送成功"); return true; }
        fail(task, result.message()); return false;
    }

    @Override
    public int retryPendingTasks(int limit) {
        int count = 0;
        int retryLimit = retryLimit();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        taskDao.stopExceededRetries(retryLimit);
        for (DmsErpSyncTask task : taskDao.selectRetryable(LocalDateTime.now(), safeLimit, retryLimit)) {
            try {
                executeTask(task);
            } catch (Exception ex) {
                // 数据库或审计设施异常时不能误写第二次失败状态；记录后继续本批其他任务。
                log.error("ERP自动推单任务处理异常，已隔离本条并继续后续任务: taskId={}", task.getId(), ex);
            }
            count++;
        }
        return count;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public boolean receiveShipment(ErpShipmentCallbackDTO callback) {
        if (callback == null || callback.getTenantId() == null || callback.getTenantId() <= 0
                || callback.getProviderCode() == null || callback.getOrderNo() == null || callback.getDeliveryNo() == null) {
            Asserts.fail("ERP发货回传参数不完整");
        }
        DmsErpIntegration integration = integrationDao.selectByTenantAndProvider(callback.getTenantId(), callback.getProviderCode());
        if (integration == null || !Integer.valueOf(1).equals(integration.getEnabled())) Asserts.fail("ERP集成未启用");
        if (!secureEquals(integration.getCallbackToken(), callback.getToken())) Asserts.fail("ERP回调鉴权失败");
        Long previousTenantId = TenantContext.getCurrentTenantId();
        try {
            // 鉴权通过后才切换到该ERP配置所属租户，订单SQL仍会执行租户过滤。
            TenantContext.setTenantId(callback.getTenantId());
            return orderShipmentService.shipErpOrder(callback.getOrderNo(), callback.getDeliveryCompany(),
                    callback.getDeliveryNo(), callback.getShipmentQuantity(), callback.getProviderCode());
        } finally {
            if (previousTenantId == null) TenantContext.clear();
            else TenantContext.setTenantId(previousTenantId);
        }
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private void fail(DmsErpSyncTask task, String error) {
        int retry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        int retryLimit = retryLimit();
        boolean stopped = retry >= retryLimit;
        String safeError = error == null || error.isBlank() ? "ERP服务返回失败" : error;
        String storedError = stopped
                ? "已达到自动重试上限（" + retryLimit + "次）：" + safeError
                : safeError;
        taskDao.markFailure(task.getId(), stopped ? 3 : 2, retry,
                stopped ? null : LocalDateTime.now().plusMinutes(Math.min(60, retry * 5L)), storedError);
        operationLogService.log("ERP", stopped ? "ORDER_PUSH_STOPPED" : "ORDER_PUSH_FAIL",
                "ERP_SYNC_TASK", String.valueOf(task.getId()), null, storedError,
                stopped ? "ERP订单推送达到上限，已停止自动重试，可由管理员人工重试"
                        : "ERP订单推送失败，等待重试");
    }

    private int retryLimit() {
        return Math.max(1, maxAutoRetries);
    }
}
