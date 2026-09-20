package com.macro.mall.distribution.service;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsWechatExpressOrderDao;
import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.entity.DmsWechatExpressOrder;
import com.macro.mall.distribution.logistics.LogisticsTrackingProvider;
import com.macro.mall.distribution.vo.ShopLogisticsTrackingVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class LogisticsTrackingService {

    private final List<LogisticsTrackingProvider> providers;
    private final String configuredProviderCode;
    @Autowired(required = false)
    private DmsWechatExpressOrderDao weChatExpressOrderDao;
    @Autowired(required = false)
    private WeChatMiniProgramGateway weChatMiniProgramGateway;

    public LogisticsTrackingService(List<LogisticsTrackingProvider> providers,
                                    @Value("${shop.logistics.tracking-provider:NONE}") String configuredProviderCode) {
        this.providers = providers == null ? List.of() : providers;
        this.configuredProviderCode = configuredProviderCode == null ? "NONE" : configuredProviderCode.trim();
    }

    public List<ShopLogisticsTrackingVO> query(List<DmsShopOrderShipment> shipments) {
        if (shipments == null || shipments.isEmpty()) return List.of();
        LogisticsTrackingProvider provider = providers.stream()
                .filter(item -> item.providerCode().equalsIgnoreCase(configuredProviderCode))
                .findFirst().orElse(null);
        return shipments.stream().map(shipment -> queryOne(shipment, provider)).toList();
    }

    private ShopLogisticsTrackingVO queryOne(DmsShopOrderShipment shipment, LogisticsTrackingProvider provider) {
        ShopLogisticsTrackingVO wechat = queryWechatExpress(shipment);
        if (wechat != null) return wechat;
        if (provider == null || shipment.getDeliveryNo() == null || shipment.getDeliveryNo().isBlank()
                || !provider.supports(shipment.getDeliveryCompany())) {
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(), shipment.getDeliveryNo(),
                    false, null, "NOT_CONFIGURED", "轨迹查询服务待开通", null, List.of());
        }
        try {
            LogisticsTrackingProvider.TrackingResult result = provider.query(
                    shipment.getDeliveryCompany(), shipment.getDeliveryNo());
            List<ShopLogisticsTrackingVO.Event> events = result == null || result.events() == null
                    ? List.of() : result.events().stream().map(event -> new ShopLogisticsTrackingVO.Event(
                    event.eventTime(), event.status(), event.description(), event.location())).toList();
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(), shipment.getDeliveryNo(),
                    true, provider.providerCode(), result == null ? "UNKNOWN" : result.status(),
                    result == null ? "物流服务暂未返回结果" : result.statusText(),
                    result == null ? null : result.updatedAt(), events);
        } catch (RuntimeException exception) {
            // 运单号可能属于个人信息，日志只记录供应商和异常类型，不打印原始单号。
            log.warn("物流轨迹查询失败 provider={}, error={}", provider.providerCode(),
                    exception.getClass().getSimpleName());
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(), shipment.getDeliveryNo(),
                    true, provider.providerCode(), "QUERY_FAILED", "物流轨迹暂时不可用，请稍后重试", null, List.of());
        }
    }

    private ShopLogisticsTrackingVO queryWechatExpress(DmsShopOrderShipment shipment) {
        if (weChatExpressOrderDao == null || weChatMiniProgramGateway == null || shipment.getId() == null) return null;
        DmsWechatExpressOrder express = weChatExpressOrderDao.selectByShipmentId(
                TenantContext.getTenantId(), shipment.getId());
        if (express == null || express.getWaybillId() == null || express.getWaybillId().isBlank()) return null;
        try {
            WeChatMiniProgramGateway.ExpressPathResult result = weChatMiniProgramGateway.getExpressPath(
                    null, express.getDeliveryId(), express.getWaybillId());
            List<ShopLogisticsTrackingVO.Event> events = result == null || result.items() == null ? List.of()
                    : result.items().stream().map(item -> new ShopLogisticsTrackingVO.Event(
                    item.actionTime() <= 0 ? null : LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(item.actionTime()), ZoneId.systemDefault()),
                    "WECHAT_" + item.actionType(), item.actionMessage(), null)).toList();
            LocalDateTime updatedAt = events.isEmpty() ? null : events.get(events.size() - 1).getEventTime();
            String statusText = events.isEmpty() ? "微信物流助手暂未返回轨迹"
                    : events.get(events.size() - 1).getDescription();
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(),
                    shipment.getDeliveryNo(), true, "WECHAT_EXPRESS", events.isEmpty() ? "PENDING" : "TRACKING",
                    statusText, updatedAt, events);
        } catch (RuntimeException exception) {
            log.warn("微信快递配送轨迹查询失败 error={}", exception.getClass().getSimpleName());
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(),
                    shipment.getDeliveryNo(), true, "WECHAT_EXPRESS", "QUERY_FAILED",
                    "微信物流轨迹暂时不可用，请稍后重试", null, List.of());
        }
    }
}
