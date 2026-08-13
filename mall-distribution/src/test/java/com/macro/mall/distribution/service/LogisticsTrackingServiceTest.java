package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsShopOrderShipment;
import com.macro.mall.distribution.logistics.LogisticsTrackingProvider;
import com.macro.mall.distribution.vo.ShopLogisticsTrackingVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsTrackingServiceTest {

    @Test
    void returnsTruthfulUnconfiguredStateWithoutInventingEvents() {
        LogisticsTrackingService service = new LogisticsTrackingService(List.of(), "NONE");

        ShopLogisticsTrackingVO result = service.query(List.of(shipment())).get(0);

        assertFalse(result.isConfigured());
        assertEquals("NOT_CONFIGURED", result.getStatus());
        assertTrue(result.getEvents().isEmpty());
    }

    @Test
    void mapsConfiguredProviderEventsWithoutChangingOrderShipmentData() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 8, 13, 10, 0);
        LogisticsTrackingProvider provider = new LogisticsTrackingProvider() {
            @Override public String providerCode() { return "CUSTOMER_PROVIDER"; }
            @Override public TrackingResult query(String company, String no) {
                return new TrackingResult("IN_TRANSIT", "运输中", eventTime,
                        List.of(new Event(eventTime, "PICKED_UP", "快件已揽收", "上海")));
            }
        };
        LogisticsTrackingService service = new LogisticsTrackingService(List.of(provider), "CUSTOMER_PROVIDER");

        ShopLogisticsTrackingVO result = service.query(List.of(shipment())).get(0);

        assertTrue(result.isConfigured());
        assertEquals("CUSTOMER_PROVIDER", result.getProviderCode());
        assertEquals("快件已揽收", result.getEvents().get(0).getDescription());
        assertEquals("上海", result.getEvents().get(0).getLocation());
    }

    private DmsShopOrderShipment shipment() {
        DmsShopOrderShipment shipment = new DmsShopOrderShipment();
        shipment.setId(1L);
        shipment.setDeliveryCompany("顺丰速运");
        shipment.setDeliveryNo("SF1234567890");
        return shipment;
    }
}
