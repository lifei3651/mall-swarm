package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderShipmentDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.dao.DmsWechatShippingSyncTaskDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.entity.DmsWechatShippingSyncTask;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeChatShippingInfoService {
    private static final Set<Integer> RETRYABLE = Set.of(-1, 40001, 40014, 42001, 10060012, 10060019);
    private static final int MAX_ATTEMPTS = 10;

    private final WeChatMiniProgramProperties miniProgramProperties;
    private final WeChatPayProperties payProperties;
    private final WeChatMiniProgramGateway gateway;
    private final DmsWechatShippingSyncTaskDao taskDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderShipmentDao shipmentDao;
    private final DmsShopOrderItemDao itemDao;
    private final DmsWechatMiniProgramIdentityDao identityDao;

    private final String workerId = "wechat-shipping-" + UUID.randomUUID();
    private volatile CompanyCache companyCache;

    /** 与本地发货事务一同落库；微信不可用不会影响本地履约。 */
    public void enqueue(DmsShopOrder order) {
        if (order == null || order.getTenantId() == null || order.getUserId() == null
                || !"WECHAT".equalsIgnoreCase(order.getPayType())
                || order.getPayTime() == null) return;
        String paymentNo = paymentNo(order);
        if (paymentNo == null) return;
        taskDao.enqueue(order.getTenantId(), paymentNo, order.getUserId());
    }

    @Scheduled(fixedDelayString = "${shop.wechat-mini-program.shipping-scan-interval-ms:15000}",
            initialDelayString = "${shop.wechat-mini-program.shipping-initial-delay-ms:30000}")
    public void scheduledSync() {
        if (!ready()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long id : taskDao.selectDueIds(now, 20)) {
            if (id == null || taskDao.claim(id, workerId, now, now.plusMinutes(2)) != 1) continue;
            try {
                syncClaimed(id);
            } catch (PermanentShippingException exception) {
                DmsWechatShippingSyncTask task = taskDao.selectById(id);
                if (task != null) permanent(task, exception.getMessage());
            } catch (RuntimeException exception) {
                DmsWechatShippingSyncTask task = taskDao.selectById(id);
                if (task != null) retry(task, "WECHAT_SHIPPING_RESULT_UNKNOWN");
                log.warn("微信发货信息同步结果未知: taskId={}", id);
            }
        }
    }

    boolean ready() {
        return miniProgramProperties.shippingInfoReady() && payProperties.isConfigured();
    }

    private void syncClaimed(Long id) {
        DmsWechatShippingSyncTask task = taskDao.selectById(id);
        if (task == null || !"SENDING".equals(task.getStatus())) return;
        List<DmsShopOrder> orders = orderDao.selectByPaymentOrderNoScoped(
                task.getTenantId(), task.getPaymentOrderNo());
        if (orders == null || orders.isEmpty() || orders.stream().anyMatch(order -> !task.getUserId().equals(order.getUserId()))) {
            permanent(task, "PAYMENT_ORDER_NOT_FOUND");
            return;
        }
        DmsWechatMiniProgramIdentity identity = identityDao.selectByUser(
                task.getTenantId(), appIdHash(), task.getUserId());
        if (identity == null || identity.getOpenId() == null || identity.getOpenId().isBlank()) {
            permanent(task, "WECHAT_IDENTITY_MISSING");
            return;
        }
        List<WeChatMiniProgramGateway.ShippingItem> shipments = shippingItems(orders);
        if (shipments.isEmpty()) { retry(task, "SHIPMENT_NOT_COMMITTED"); return; }
        if (shipments.size() > 15) { permanent(task, "WECHAT_SHIPMENT_LIMIT_EXCEEDED"); return; }
        // 关闭/退款订单不等于已经完成实物发货，不能把微信侧的“全部发货”提前置为真。
        boolean allDelivered = orders.stream().allMatch(order -> order.getStatus() != null
                && Set.of(2, 3).contains(order.getStatus()));
        WeChatMiniProgramGateway.ShippingInfoResult result = gateway.uploadShippingInfo(
                new WeChatMiniProgramGateway.ShippingInfoCommand(payProperties.getMchId().trim(),
                        task.getPaymentOrderNo(), identity.getOpenId(), allDelivered, shipments));
        int code = result == null ? -1 : result.errorCode();
        if (result != null && result.success()) {
            taskDao.markSuccess(task.getId(), workerId, task.getRevision(), payloadDigest(task, shipments), LocalDateTime.now());
        } else if (RETRYABLE.contains(code)) {
            retry(task, "WECHAT_" + code);
        } else {
            permanent(task, "WECHAT_" + code);
        }
    }

    private List<WeChatMiniProgramGateway.ShippingItem> shippingItems(List<DmsShopOrder> orders) {
        Map<String, MutableShipment> unique = new LinkedHashMap<>();
        Map<String, String> companies = deliveryCompanies();
        for (DmsShopOrder order : orders) {
            String description = itemDescription(order.getId());
            String contact = maskContact(order.getReceiverPhone());
            for (DmsShopOrderShipment shipment : shipmentDao.selectByOrderId(order.getId())) {
                String companyId = resolveCompany(companies, shipment.getDeliveryCompany());
                if (companyId == null) throw new PermanentShippingException("EXPRESS_COMPANY_NOT_FOUND");
                String key = companyId + "\u0000" + shipment.getDeliveryNo();
                unique.compute(key, (ignored, existing) -> existing == null
                        ? new MutableShipment(shipment.getDeliveryNo(), companyId, description, contact)
                        : existing.mergeDescription(description));
            }
        }
        List<WeChatMiniProgramGateway.ShippingItem> result = new ArrayList<>();
        for (MutableShipment shipment : unique.values()) result.add(shipment.toGateway());
        return List.copyOf(result);
    }

    private Map<String, String> deliveryCompanies() {
        CompanyCache current = companyCache;
        if (current != null && current.expiresAt().isAfter(Instant.now())) return current.companies();
        synchronized (this) {
            current = companyCache;
            if (current != null && current.expiresAt().isAfter(Instant.now())) return current.companies();
            Map<String, String> rows = new LinkedHashMap<>();
            for (WeChatMiniProgramGateway.DeliveryCompany company : gateway.deliveryCompanies()) {
                rows.put(company.id().trim().toLowerCase(Locale.ROOT), company.id().trim());
                rows.put(normalizeCompany(company.name()), company.id().trim());
            }
            current = new CompanyCache(Map.copyOf(rows), Instant.now().plusSeconds(21600));
            companyCache = current;
            return current.companies();
        }
    }

    private String resolveCompany(Map<String, String> companies, String value) {
        if (value == null || value.isBlank()) return null;
        String direct = companies.get(value.trim().toLowerCase(Locale.ROOT));
        return direct == null ? companies.get(normalizeCompany(value)) : direct;
    }

    private String normalizeCompany(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s()（）]", "")
                .replace("股份有限公司", "").replace("有限公司", "")
                .replace("速运", "").replace("快递", "").replace("物流", "");
    }

    private String itemDescription(Long orderId) {
        List<DmsShopOrderItem> items = itemDao.selectByOrderId(orderId);
        if (items == null || items.isEmpty()) return "商城商品";
        String first = items.get(0).getProductName();
        int quantity = items.stream().map(DmsShopOrderItem::getQuantity).filter(value -> value != null)
                .mapToInt(Integer::intValue).sum();
        String value = (first == null || first.isBlank() ? "商城商品" : first.trim())
                + (items.size() > 1 ? "等" : "") + "*" + Math.max(1, quantity) + "件";
        return limitCodePoints(value, 120);
    }

    private String maskContact(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) return null;
        String prefix = digits.length() >= 7 ? digits.substring(0, 3) : "";
        return prefix + "****" + digits.substring(digits.length() - 4);
    }

    private void retry(DmsWechatShippingSyncTask task, String code) {
        if (task.getAttemptCount() != null && task.getAttemptCount() >= MAX_ATTEMPTS) {
            permanent(task, "MAX_ATTEMPTS_REACHED");
            return;
        }
        int attempt = Math.max(1, task.getAttemptCount() == null ? 1 : task.getAttemptCount());
        long seconds = Math.min(21600L, 60L * (1L << Math.min(8, attempt - 1)));
        taskDao.markRetry(task.getId(), workerId, task.getRevision(), LocalDateTime.now().plusSeconds(seconds), code);
    }

    private void permanent(DmsWechatShippingSyncTask task, String code) {
        taskDao.markPermanent(task.getId(), workerId, task.getRevision(), safeCode(code), LocalDateTime.now());
    }

    private String payloadDigest(DmsWechatShippingSyncTask task, List<WeChatMiniProgramGateway.ShippingItem> shipments) {
        StringBuilder value = new StringBuilder(task.getPaymentOrderNo()).append('|').append(task.getRevision());
        for (WeChatMiniProgramGateway.ShippingItem shipment : shipments) {
            value.append('|').append(shipment.expressCompany()).append(':').append(shipment.trackingNo());
        }
        return SecureUtil.sha256(value.toString());
    }

    private String paymentNo(DmsShopOrder order) {
        String value = order.getPaymentOrderNo();
        if (value == null || value.isBlank()) value = order.getOrderNo();
        return value == null || value.isBlank() ? null : value.trim();
    }
    private String appIdHash() { return SecureUtil.sha256(miniProgramProperties.getAppId().trim()); }
    private String safeCode(String value) { return value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_.-]", "_"); }
    private String limitCodePoints(String value, int max) {
        int count = value.codePointCount(0, value.length());
        return count <= max ? value : value.substring(0, value.offsetByCodePoints(0, max));
    }

    private record CompanyCache(Map<String, String> companies, Instant expiresAt) { }
    private static final class PermanentShippingException extends RuntimeException {
        private PermanentShippingException(String message) { super(message); }
    }
    private final class MutableShipment {
        private final String trackingNo;
        private final String companyId;
        private String description;
        private final String contact;
        private MutableShipment(String trackingNo, String companyId, String description, String contact) {
            this.trackingNo = trackingNo; this.companyId = companyId; this.description = description; this.contact = contact;
        }
        private MutableShipment mergeDescription(String next) {
            if (next != null && !next.isBlank() && !description.contains(next)) description = limitCodePoints(description + "、" + next, 120);
            return this;
        }
        private WeChatMiniProgramGateway.ShippingItem toGateway() {
            return new WeChatMiniProgramGateway.ShippingItem(trackingNo, companyId, description, contact);
        }
    }
}
