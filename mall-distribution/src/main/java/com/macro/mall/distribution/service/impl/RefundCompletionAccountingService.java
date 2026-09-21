package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopOrderItemDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.dto.FinanceRefundDTO;
import com.macro.mall.distribution.entity.DmsFinanceRefund;
import com.macro.mall.distribution.entity.DmsShopAfterSale;
import com.macro.mall.distribution.entity.DmsShopAfterSaleItem;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopOrderItem;
import com.macro.mall.distribution.service.DistributionAuditService;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.util.ShopQuantityChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies the local accounting effects of a completed refund.
 *
 * <p>External payment refunds must call this service only after the payment
 * channel has confirmed success.  All effects join the caller's transaction,
 * so a failed completion cannot leave finance, wallet, merchant settlement or
 * company allocations partially reversed.</p>
 */
@Service
@RequiredArgsConstructor
public class RefundCompletionAccountingService {

    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DistributionAuditService auditService;
    private final MemberAssetService memberAssetService;
    private final OrderBalanceAllocationService orderBalanceAllocationService;
    private final MerchantService merchantService;

    /**
     * @return {@code true} when accounting was newly applied, {@code false}
     * when the same refund number had already been applied by an earlier
     * transaction (including refunds created before this completion gate).
     */
    public boolean complete(DmsShopAfterSale afterSale, DmsShopOrder order) {
        if (afterSale == null || order == null) {
            throw new IllegalArgumentException("退款售后单和订单不能为空");
        }
        List<DmsFinanceRefund> existing = auditService.getRefundsByOrderId(order.getId());
        if (existing != null && existing.stream()
                .anyMatch(refund -> Objects.equals(afterSale.getAfterSaleNo(), refund.getRefundNo()))) {
            return false;
        }

        List<DmsShopAfterSaleItem> items = afterSaleItemDao.selectByAfterSaleId(afterSale.getId());
        ShopQuantityChecks.refundLines(items);
        if (afterSaleItemDao.countInvalidReservedItemsByOrderId(order.getId()) != 0) {
            throw new IllegalStateException("历史售后数量或商品归属异常，禁止完成退款账务");
        }
        List<DmsShopOrderItem> orderItems = orderItemDao.selectByOrderId(order.getId());
        Map<Long, DmsShopOrderItem> orderItemsById = new LinkedHashMap<>();
        for (DmsShopOrderItem orderItem : orderItems) orderItemsById.put(orderItem.getId(), orderItem);
        BigDecimal bonusRefundAmount = items.stream()
                .filter(item -> isBonusEligibleOrderItem(orderItemsById.get(item.getOrderItemId())))
                .map(item -> item.getCouponBonusRefundAmount() == null
                        ? item.getRefundAmount() : item.getCouponBonusRefundAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int bonusRefundQuantity = items.stream()
                .filter(item -> isBonusEligibleOrderItem(orderItemsById.get(item.getOrderItemId())))
                .map(DmsShopAfterSaleItem::getRefundQuantity)
                .filter(Objects::nonNull)
                .reduce(0, ShopQuantityChecks::add);

        FinanceRefundDTO refundDTO = new FinanceRefundDTO();
        refundDTO.setOrderId(afterSale.getOrderId());
        refundDTO.setOrderNo(afterSale.getOrderNo());
        refundDTO.setRefundNo(afterSale.getAfterSaleNo());
        refundDTO.setRefundAmount(afterSale.getRefundAmount());
        refundDTO.setProductRefundAmount(afterSale.getProductRefundAmount());
        refundDTO.setFreightRefundAmount(afterSale.getFreightRefundAmount());
        refundDTO.setRefundQuantity(afterSale.getRefundQuantity());
        refundDTO.setBonusBaseAmount(calculateBonusBase(order, orderItems));
        refundDTO.setBonusRefundAmount(bonusRefundAmount);
        refundDTO.setBonusRefundQuantity(bonusRefundQuantity);
        refundDTO.setCumulativeBonusRefundAmount(
                nullToZero(afterSaleItemDao.sumApprovedBonusRefundByOrderId(order.getId())));
        refundDTO.setClawbackBonus(1);
        refundDTO.setReason("售后退款：" + afterSale.getReason());
        refundDTO.setOperatorId(afterSale.getAuditUserId());
        refundDTO.setOperatorName(afterSale.getAuditUserName());
        auditService.saveRefund(refundDTO);

        // These four effects form one completion transaction with the finance refund.
        orderBalanceAllocationService.recalculateAfterRefund(afterSale.getOrderId(), afterSale.getId());
        if ("BALANCE".equalsIgnoreCase(order.getPayType())
                && afterSale.getRefundAmount() != null
                && afterSale.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            AssetChangeDTO balanceRefund = new AssetChangeDTO();
            balanceRefund.setUserId(order.getUserId());
            balanceRefund.setAmount(afterSale.getRefundAmount());
            balanceRefund.setBizType("BALANCE_PAYMENT_REFUND");
            balanceRefund.setBizId(String.valueOf(afterSale.getId()));
            balanceRefund.setRequestId("BALANCE_PAYMENT_REFUND-" + afterSale.getId());
            balanceRefund.setRemark("余额支付售后退款：" + afterSale.getAfterSaleNo());
            memberAssetService.issue(balanceRefund);
        }
        merchantService.reverseAfterSaleItems(items);
        return true;
    }

    private BigDecimal calculateBonusBase(DmsShopOrder order, List<DmsShopOrderItem> items) {
        if (order.getCouponClaimId() != null) {
            if (items == null || items.isEmpty()
                    || items.stream().anyMatch(item -> item.getCouponBonusBaseAmount() == null)) {
                throw new IllegalStateException("优惠订单奖金快照缺失");
            }
            return items.stream().filter(this::isBonusEligibleOrderItem)
                    .map(DmsShopOrderItem::getCouponBonusBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        if (items == null || items.isEmpty()) {
            BigDecimal productAmount = order.getTotalAmount() == null
                    ? nullToZero(order.getPayAmount()).subtract(nullToZero(order.getFreightAmount()))
                    : nullToZero(order.getTotalAmount());
            return productAmount.subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal gross = items.stream().map(DmsShopOrderItem::getTotalAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal eligible = items.stream().filter(this::isBonusEligibleOrderItem)
                .map(DmsShopOrderItem::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.compareTo(BigDecimal.ZERO) <= 0 || eligible.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal eligibleDiscount = nullToZero(order.getDiscountAmount()).multiply(eligible)
                .divide(gross, 2, RoundingMode.HALF_UP);
        return eligible.subtract(eligibleDiscount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBonusEligibleOrderItem(DmsShopOrderItem item) {
        String mode = item == null ? null : item.getTeamBonusMode();
        return mode == null || mode.isBlank() || "INHERIT".equalsIgnoreCase(mode)
                || "STANDARD".equalsIgnoreCase(mode);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
