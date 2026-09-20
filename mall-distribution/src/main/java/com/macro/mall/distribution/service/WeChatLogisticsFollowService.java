package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsWechatLogisticsFollowTaskDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatLogisticsFollowTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** 发货后自动向微信登记物流消息；查询组件凭证仍按用户每次查看即时换取。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeChatLogisticsFollowService {
    private static final int MAX_ATTEMPTS = 10;
    private static final Set<Integer> ACTIVE_ORDER_STATUSES = Set.of(1, 2, 3);

    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatPayProperties payProperties;
    private final DmsWechatLogisticsFollowTaskDao taskDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderShipmentDao shipmentDao;
    private final WeChatLogisticsQueryService queryService;

    private final String workerId = "wechat-logistics-follow-" + UUID.randomUUID();

    /** 与本地发货事务一同入队；外部微信接口由后台任务调用，不阻塞发货。 */
    public void enqueue(DmsShopOrder order, DmsShopOrderShipment shipment) {
        if (!eligible(order) || shipment == null || shipment.getId() == null) return;
        taskDao.enqueue(tenantId(order), order.getId(), shipment.getId(), order.getUserId());
    }

    @Scheduled(fixedDelayString = "${shop.wechat-mini-program.logistics-follow-scan-interval-ms:15000}",
            initialDelayString = "${shop.wechat-mini-program.logistics-follow-initial-delay-ms:45000}")
    public void scheduledRegister() {
        if (!ready()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : taskDao.selectDueIds(now, 20)) {
            if (id == null || taskDao.claim(id, workerId, now, now.plusMinutes(2)) != 1) continue;
            try {
                registerClaimed(id);
            } catch (RuntimeException exception) {
                DmsWechatLogisticsFollowTask task = taskDao.selectById(id);
                if (task != null) retry(task, "WECHAT_LOGISTICS_RESULT_UNKNOWN");
                log.warn("微信物流消息登记结果未知: taskId={}, type={}", id,
                        exception.getClass().getSimpleName());
            }
        }
    }

    boolean ready() {
        return miniProgramProperties.loginReady() && payProperties.isConfigured();
    }

    private void registerClaimed(Long id) {
        DmsWechatLogisticsFollowTask task = taskDao.selectById(id);
        if (task == null || !"SENDING".equals(task.getStatus())) return;
        DmsShopOrder order = orderDao.selectByIdScoped(task.getTenantId(), task.getOrderId());
        if (order == null || !task.getUserId().equals(order.getUserId())) {
            terminal(task, "PERMANENT", "ORDER_NOT_FOUND");
            return;
        }
        if (!eligible(order)) {
            terminal(task, "SKIPPED", "ORDER_NOT_ELIGIBLE");
            return;
        }
        DmsShopOrderShipment shipment = shipmentDao.selectByIdScoped(
                task.getTenantId(), task.getOrderId(), task.getShipmentId());
        if (shipment == null || blank(shipment.getDeliveryNo())) {
            terminal(task, "PERMANENT", "SHIPMENT_NOT_FOUND");
            return;
        }
        queryService.register(task.getTenantId(), order, shipment);
        taskDao.markSuccess(task.getId(), workerId, digest(order, shipment), LocalDateTime.now());
    }

    private void retry(DmsWechatLogisticsFollowTask task, String code) {
        if (task.getAttemptCount() != null && task.getAttemptCount() >= MAX_ATTEMPTS) {
            terminal(task, "PERMANENT", "MAX_ATTEMPTS_REACHED");
            return;
        }
        int attempt = Math.max(1, task.getAttemptCount() == null ? 1 : task.getAttemptCount());
        long seconds = Math.min(21_600L, 60L * (1L << Math.min(8, attempt - 1)));
        taskDao.markRetry(task.getId(), workerId, LocalDateTime.now().plusSeconds(seconds), code);
    }

    private void terminal(DmsWechatLogisticsFollowTask task, String status, String code) {
        taskDao.markTerminal(task.getId(), workerId, status, code, LocalDateTime.now());
    }

    private boolean eligible(DmsShopOrder order) {
        return order != null && order.getId() != null && order.getUserId() != null
                && "WECHAT".equalsIgnoreCase(order.getPayType()) && order.getPayTime() != null
                && order.getStatus() != null && ACTIVE_ORDER_STATUSES.contains(order.getStatus());
    }

    private Long tenantId(DmsShopOrder order) {
        return order.getTenantId() == null ? 1L : order.getTenantId();
    }

    private String digest(DmsShopOrder order, DmsShopOrderShipment shipment) {
        return SecureUtil.sha256(tenantId(order) + "|" + order.getId() + "|" + shipment.getId()
                + "|" + shipment.getDeliveryCompany() + "|" + shipment.getDeliveryNo());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

}
