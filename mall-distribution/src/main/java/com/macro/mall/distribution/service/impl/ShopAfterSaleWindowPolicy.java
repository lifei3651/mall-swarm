package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Resolves one tenant's customer-facing after-sale application window.
 * Orders may still be handled manually by an administrator after this entry window closes.
 */
@Component
@RequiredArgsConstructor
public class ShopAfterSaleWindowPolicy {

    public static final String MODE_RECEIVED = "RECEIVED";
    public static final String MODE_ORDER_CREATED = "ORDER_CREATED";

    private final DmsTenantDao tenantDao;

    public Window resolve(Long tenantId) {
        DmsTenant tenant = tenantDao.selectById(tenantId == null ? 1L : tenantId);
        String mode = tenant == null ? null : tenant.getAfterSaleWindowMode();
        int days = tenant == null || tenant.getAfterSaleWindowDays() == null
                ? 7 : Math.max(0, Math.min(365, tenant.getAfterSaleWindowDays()));
        if (!MODE_ORDER_CREATED.equals(mode)) {
            mode = MODE_RECEIVED;
        }
        return new Window(mode, days);
    }

    public LocalDateTime deadline(DmsShopOrder order) {
        if (order == null) return null;
        return deadline(order, resolve(order.getTenantId()));
    }

    public LocalDateTime deadline(DmsShopOrder order, Window window) {
        if (order == null || window == null) return null;
        if (window.days() == 0) return order.getCreateTime();
        LocalDateTime start = MODE_ORDER_CREATED.equals(window.mode())
                ? order.getCreateTime() : order.getReceiveTime();
        return start == null ? null : start.plusDays(window.days());
    }

    public String label(DmsShopOrder order) {
        return label(resolve(order == null ? null : order.getTenantId()));
    }

    public String label(Window window) {
        if (window.days() == 0) return "客户自助售后入口已关闭";
        return (MODE_ORDER_CREATED.equals(window.mode()) ? "下单后" : "签收后") + window.days() + "天";
    }

    public boolean isExpired(DmsShopOrder order, LocalDateTime now) {
        LocalDateTime deadline = deadline(order);
        return deadline != null && !now.isBefore(deadline);
    }

    public boolean isExpired(DmsShopOrder order, LocalDateTime now, Window window) {
        if (window != null && window.days() == 0) return true;
        LocalDateTime deadline = deadline(order, window);
        return deadline != null && !now.isBefore(deadline);
    }

    public record Window(String mode, int days) {
    }
}
