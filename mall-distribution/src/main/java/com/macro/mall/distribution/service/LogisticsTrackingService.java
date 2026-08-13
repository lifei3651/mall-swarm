package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.logistics.LogisticsTrackingProvider;
import com.macro.mall.distribution.vo.ShopLogisticsTrackingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class LogisticsTrackingService {

    private final List<LogisticsTrackingProvider> providers;
    private final String configuredProviderCode;

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
        if (provider == null || shipment.getDeliveryNo() == null || shipment.getDeliveryNo().isBlank()
                || !provider.supports(shipment.getDeliveryCompany())) {
            return new ShopLogisticsTrackingVO(shipment.getId(), shipment.getDeliveryCompany(), shipment.getDeliveryNo(),
                    false, null, "NOT_CONFIGURED", "暂未接入真实物流轨迹", null, List.of());
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
}
