package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.entity.DmsShopAfterSale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 售后下一责任方与处理时限只用于进度透明和平台优先级，不直接触发自动退款。
 */
@Component
public class ShopAfterSaleTimelinePolicy {

    @Value("${shop.after-sale.merchant-audit-timeout-hours:48}")
    private int merchantAuditTimeoutHours;

    @Value("${shop.after-sale.return-shipment-timeout-days:7}")
    private int returnShipmentTimeoutDays;

    @Value("${shop.after-sale.merchant-return-confirm-timeout-days:7}")
    private int merchantReturnConfirmTimeoutDays;

    @Value("${shop.after-sale.merchant-exchange-shipment-timeout-days:3}")
    private int merchantExchangeShipmentTimeoutDays;

    @Value("${shop.after-sale.exchange-auto-receive-days:15}")
    private int exchangeAutoReceiveDays;

    public DmsShopAfterSale enrich(DmsShopAfterSale sale) {
        if (sale == null) return null;
        sale.setNextActionDeadline(null);
        sale.setNextActionOverdue(false);
        sale.setNextActionParty(null);
        sale.setNextActionHint(null);
        Integer status = sale.getStatus();
        if (Integer.valueOf(0).equals(status)) {
            apply(sale, "MERCHANT", plusHours(sale.getCreateTime(), safeAuditHours()),
                    "等待商家审核", "商家审核已超时，请平台客服优先介入");
        } else if (Integer.valueOf(4).equals(status)) {
            LocalDateTime deadline = returnShipmentTimeoutDays <= 0 ? null
                    : plusDays(sale.getAuditTime(), Math.min(returnShipmentTimeoutDays, 365));
            apply(sale, "MEMBER", deadline,
                    "请按审核结果寄回商品", "退货寄回期限已到，请联系商城客服");
        } else if (Integer.valueOf(5).equals(status)) {
            LocalDateTime base = sale.getReturnShippedAt() == null ? sale.getUpdateTime() : sale.getReturnShippedAt();
            apply(sale, "MERCHANT", plusDays(base, safeReturnConfirmDays()),
                    "等待商家确认收到退货", "商家确认退货已超时，请平台客服优先介入");
        } else if (Integer.valueOf(6).equals(status)) {
            sale.setNextActionParty("PLATFORM");
            sale.setNextActionHint("退款通道处理中，请勿重复提交");
        } else if (Integer.valueOf(7).equals(status)) {
            LocalDateTime base = sale.getReturnReceivedAt() == null ? sale.getUpdateTime() : sale.getReturnReceivedAt();
            apply(sale, "MERCHANT", plusDays(base, safeExchangeShipmentDays()),
                    "退件已收货，等待商家发出同规格替换商品", "换货商品补发已超时，请平台客服优先介入");
        } else if (Integer.valueOf(8).equals(status)) {
            LocalDateTime deadline = exchangeAutoReceiveDays <= 0 ? null
                    : plusDays(sale.getExchangeShippedAt(), Math.min(exchangeAutoReceiveDays, 365));
            apply(sale, "MEMBER", deadline,
                    "替换商品已发出，请确认收货", "替换商品已到自动确认期限，将由系统完成换货");
        } else if (Integer.valueOf(2).equals(status)) {
            sale.setNextActionParty("MEMBER");
            sale.setNextActionHint("申请已被拒绝，可在售后期限内补充凭证后重新申请或联系客服");
        }
        return sale;
    }

    private void apply(DmsShopAfterSale sale, String party, LocalDateTime deadline,
                       String normalHint, String overdueHint) {
        boolean overdue = deadline != null && !deadline.isAfter(LocalDateTime.now());
        sale.setNextActionParty(party);
        sale.setNextActionDeadline(deadline);
        sale.setNextActionOverdue(overdue);
        sale.setNextActionHint(overdue ? overdueHint : normalHint);
    }

    private int safeAuditHours() {
        return Math.max(1, Math.min(merchantAuditTimeoutHours, 24 * 30));
    }

    private int safeReturnConfirmDays() {
        return Math.max(1, Math.min(merchantReturnConfirmTimeoutDays, 365));
    }

    private int safeExchangeShipmentDays() {
        return Math.max(1, Math.min(merchantExchangeShipmentTimeoutDays, 365));
    }

    private LocalDateTime plusHours(LocalDateTime value, int hours) {
        return value == null ? null : value.plusHours(hours);
    }

    private LocalDateTime plusDays(LocalDateTime value, int days) {
        return value == null ? null : value.plusDays(days);
    }
}
