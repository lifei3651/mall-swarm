package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsCommissionClawbackDao;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsFinanceRefundDao;
import com.macro.mall.distribution.dao.DmsOrderBalanceAllocationDao;
import com.macro.mall.distribution.dao.DmsOrderFinanceDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import com.macro.mall.distribution.dao.DmsShopAfterSaleDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dto.AssetChangeDTO;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsOrderBalanceAllocation;
import com.macro.mall.distribution.entity.DmsOrderFinance;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.enums.CommissionStatusEnum;
import com.macro.mall.distribution.service.MemberAssetService;
import com.macro.mall.distribution.service.OrderBalanceAllocationService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品资金归集：产品成本与剩余商品款进入独立的系统资金账户，不占用客户登录账号。
 * 运费不参与归集；两类资金均在订单达到租户实际售后期限且无待处理售后后进入真实余额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBalanceAllocationServiceImpl implements OrderBalanceAllocationService {

    private final DmsOrderBalanceAllocationDao allocationDao;
    private final DmsShopOrderDao orderDao;
    private final DmsOrderFinanceDao financeDao;
    private final DmsFinanceRefundDao refundDao;
    private final DmsShopAfterSaleItemDao afterSaleItemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsCommissionRecordDao commissionRecordDao;
    private final DmsCommissionClawbackDao clawbackDao;
    private final DmsShopMemberDao memberDao;
    private final DmsAgentDao agentDao;
    private final MemberAssetService memberAssetService;
    private final PlatformTransactionManager transactionManager;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;

    @Value("${order.balance-allocation.remainder-account:SYSTEM_REMAINDER}")
    private String remainderAccount;

    @Value("${order.balance-allocation.product-cost-account:SYSTEM_PRODUCT_COST}")
    private String productCostAccount;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DmsOrderBalanceAllocation> prepareForOrder(Long orderId) {
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null || order.getPayTime() == null || order.getStatus() == null
                || order.getStatus() < 1 || order.getStatus() > 3) {
            return List.of();
        }
        DmsOrderFinance finance = financeDao.selectByOrderId(orderId);
        if (finance == null) {
            log.warn("订单资金归集等待账务计算完成: orderId={}", orderId);
            return List.of();
        }

        AllocationAmounts amounts = calculateAmounts(order, finance);
        // 第三方商户的成本款进入商户货款账户，不能再重复进入平台商品成本账户。
        if (order.getMerchantId() == null) {
            createIfMissing(order, PRODUCT_COST, productCostAccount, amounts.originalCost(), amounts.currentCost());
        }
        createIfMissing(order, REMAINDER, remainderAccount, amounts.originalRemainder(), amounts.currentRemainder());
        return hydrate(allocationDao.selectByOrderId(orderId));
    }

    @Override
    public int prepareMissingOrders(int limit) {
        int prepared = 0;
        int safeLimit = Math.max(1, Math.min(limit, 500));
        for (Long orderId : allocationDao.selectMissingOrderIds(TenantContext.getTenantId(), safeLimit)) {
            try {
                Integer count = new TransactionTemplate(transactionManager).execute(status -> {
                    int before = allocationDao.selectByOrderId(orderId).size();
                    int after = prepareForOrder(orderId).size();
                    return Math.max(0, after - before);
                });
                prepared += count == null ? 0 : count;
            } catch (Exception ex) {
                log.error("历史订单资金归集凭证补建失败，已隔离当前订单并继续扫描: orderId={}", orderId, ex);
            }
        }
        return prepared;
    }

    @Override
    public int settleEligibleAfterCoolingOff(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        // 兼容上线前已经支付/收货的订单；缺失目标账号时不会影响用户支付，账号准备好后会自动补建。
        prepareMissingOrders(safeLimit);
        LocalDateTime cutoff = LocalDateTime.now();
        int settled = 0;
        for (Long allocationId : allocationDao.selectEligibleIds(TenantContext.getTenantId(), cutoff, safeLimit)) {
            try {
                Boolean success = new TransactionTemplate(transactionManager).execute(status -> settleOne(allocationId));
                if (Boolean.TRUE.equals(success)) settled++;
            } catch (Exception ex) {
                log.error("订单资金归集结算失败，已隔离当前明细并继续扫描: allocationId={}", allocationId, ex);
            }
        }
        return settled;
    }

    private boolean settleOne(Long allocationId) {
        DmsOrderBalanceAllocation allocation = allocationDao.selectByIdForUpdate(allocationId);
        if (allocation == null || !Integer.valueOf(0).equals(allocation.getStatus())
                || nullToZero(allocation.getCurrentAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        DmsShopOrder order = orderDao.selectByIdForUpdate(allocation.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        if (order == null || !Integer.valueOf(3).equals(order.getStatus()) || order.getReceiveTime() == null) return false;
        LocalDateTime afterSaleDeadline = afterSaleWindowPolicy.deadline(order);
        if (afterSaleDeadline != null && now.isBefore(afterSaleDeadline)) return false;
        if (afterSaleDao.selectOpenByOrderId(order.getId()) != null) return false;
        AssetChangeDTO credit = new AssetChangeDTO();
        credit.setAgentId(allocation.getTargetAgentId());
        credit.setAmount(money(allocation.getCurrentAmount()));
        credit.setBizType("ORDER_BALANCE_ALLOCATION");
        credit.setBizId(String.valueOf(allocation.getId()));
        credit.setRequestId("ORDER-ALLOC-SETTLE-" + allocation.getId());
        credit.setRemark(allocationRemark(allocation) + "，订单：" + allocation.getOrderNo());
        memberAssetService.issueSystem(credit);
        return allocationDao.markSettled(allocation.getId(), credit.getAmount(), LocalDateTime.now(), 1) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateAfterRefund(Long orderId, Long refundId) {
        List<DmsOrderBalanceAllocation> rows = allocationDao.selectByOrderId(orderId);
        if (rows.isEmpty()) rows = prepareForOrder(orderId);
        if (rows.isEmpty()) return;

        DmsShopOrder order = orderDao.selectById(orderId);
        DmsOrderFinance finance = financeDao.selectByOrderId(orderId);
        if (order == null || finance == null) return;
        AllocationAmounts amounts = calculateAmounts(order, finance);

        for (DmsOrderBalanceAllocation summary : rows) {
            DmsOrderBalanceAllocation row = allocationDao.selectByOrderIdAndTypeForUpdate(
                    orderId, summary.getAllocationType());
            if (row == null) continue;
            BigDecimal calculated = PRODUCT_COST.equals(row.getAllocationType())
                    ? amounts.currentCost() : amounts.currentRemainder();
            // 退款只能减少既有应归集金额；防止累计比例和分币舍入使退款后反向增加余额。
            BigDecimal targetAmount = money(calculated).min(nullToZero(row.getCurrentAmount()));
            if (nullToZero(row.getSettledAmount()).compareTo(BigDecimal.ZERO) <= 0) {
                allocationDao.updatePendingAmount(row.getId(), targetAmount,
                        targetAmount.compareTo(BigDecimal.ZERO) == 0 ? 2 : 0);
                continue;
            }

            BigDecimal netCredited = nullToZero(row.getSettledAmount())
                    .subtract(nullToZero(row.getReversedAmount())).max(BigDecimal.ZERO);
            BigDecimal reverseAmount = netCredited.subtract(targetAmount).max(BigDecimal.ZERO);
            if (reverseAmount.compareTo(BigDecimal.ZERO) > 0) {
                AssetChangeDTO debit = new AssetChangeDTO();
                debit.setAgentId(row.getTargetAgentId());
                debit.setAmount(money(reverseAmount));
                debit.setBizType("ORDER_BALANCE_ALLOCATION_REFUND");
                debit.setBizId(refundId == null ? String.valueOf(orderId) : String.valueOf(refundId));
                debit.setRequestId("ORDER-ALLOC-REFUND-" + row.getId() + "-" + (refundId == null ? orderId : refundId));
                debit.setRemark("退款冲回" + allocationRemark(row) + "，订单：" + row.getOrderNo());
                memberAssetService.deductSystemAllowNegative(debit);
            }
            BigDecimal totalReversed = nullToZero(row.getReversedAmount()).add(reverseAmount);
            allocationDao.updateAfterReversal(row.getId(), targetAmount, totalReversed,
                    targetAmount.compareTo(BigDecimal.ZERO) == 0 ? 2 : 1);
        }
    }

    @Override
    public List<DmsOrderBalanceAllocation> listByOrderId(Long orderId) {
        return hydrate(allocationDao.selectByOrderId(orderId));
    }

    private void createIfMissing(DmsShopOrder order, String type, String account,
                                 BigDecimal originalAmount, BigDecimal currentAmount) {
        if (allocationDao.selectByOrderIdAndTypeForUpdate(order.getId(), type) != null) return;
        Target target = resolveTarget(account);
        if (target == null) {
            log.error("订单资金归集目标尚未准备好，不阻断订单支付，稍后自动重试: orderId={}, type={}, account={}",
                    order.getId(), type, account);
            return;
        }
        DmsOrderBalanceAllocation allocation = new DmsOrderBalanceAllocation();
        allocation.setTenantId(order.getTenantId() == null ? 1L : order.getTenantId());
        allocation.setOrderId(order.getId());
        allocation.setOrderNo(order.getOrderNo());
        allocation.setAllocationType(type);
        allocation.setTargetMemberId(target.member().getId());
        allocation.setTargetUserId(target.member().getUserId());
        allocation.setTargetAgentId(target.agent().getId());
        allocation.setOriginalAmount(money(originalAmount));
        allocation.setCurrentAmount(money(currentAmount));
        allocation.setSettledAmount(BigDecimal.ZERO.setScale(2));
        allocation.setReversedAmount(BigDecimal.ZERO.setScale(2));
        allocation.setStatus(allocation.getCurrentAmount().compareTo(BigDecimal.ZERO) > 0 ? 0 : 2);
        allocationDao.insert(allocation);
    }

    private Target resolveTarget(String account) {
        if (account == null || account.isBlank()) return null;
        String value = account.trim();
        DmsShopMember member = memberDao.selectByUsername(value);
        if (member == null) return null;
        DmsAgent agent = agentDao.selectByUserId(member.getUserId());
        return agent == null ? null : new Target(member, agent);
    }

    private AllocationAmounts calculateAmounts(DmsShopOrder order, DmsOrderFinance finance) {
        BigDecimal productBase = nullToZero(order.getTotalAmount())
                .subtract(nullToZero(order.getDiscountAmount())).max(BigDecimal.ZERO);
        BigDecimal originalCost = money(nullToZero(order.getTotalCost()));
        BigDecimal refundedCost = money(nullToZero(afterSaleItemDao.sumApprovedCostByOrderId(order.getId())));
        BigDecimal currentCost = originalCost.subtract(refundedCost).max(BigDecimal.ZERO);
        BigDecimal productRefund = money(nullToZero(refundDao.sumProductByOrderId(order.getId())));
        BigDecimal currentProductBase = productBase.subtract(productRefund).max(BigDecimal.ZERO);
        BigDecimal originalBonus = originalBonus(order.getId());
        BigDecimal currentBonus = money(nullToZero(finance.getBonusAmount()));
        BigDecimal originalRemainder = productBase.subtract(originalCost).subtract(originalBonus).max(BigDecimal.ZERO);
        BigDecimal currentRemainder = currentProductBase.subtract(currentCost).subtract(currentBonus).max(BigDecimal.ZERO);
        return new AllocationAmounts(money(originalCost), money(currentCost), money(originalRemainder), money(currentRemainder));
    }

    private BigDecimal originalBonus(Long orderId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DmsCommissionRecord record : commissionRecordDao.selectByOrderId(orderId)) {
            CommissionStatusEnum status = CommissionStatusEnum.getByValue(record.getStatus());
            if (status == CommissionStatusEnum.CANCELLED) continue;
            BigDecimal amount = nullToZero(record.getCommissionAmount());
            if (status == CommissionStatusEnum.PENDING || status == CommissionStatusEnum.REFUNDED) {
                amount = amount.add(nullToZero(clawbackDao.sumByCommissionRecordId(record.getId())));
            }
            total = total.add(amount);
        }
        return money(total);
    }

    private List<DmsOrderBalanceAllocation> hydrate(List<DmsOrderBalanceAllocation> rows) {
        List<DmsOrderBalanceAllocation> result = new ArrayList<>(rows);
        for (DmsOrderBalanceAllocation row : result) {
            DmsShopMember member = memberDao.selectById(row.getTargetMemberId());
            row.setTargetAccount(MemberAccountUtils.display(member));
        }
        return result;
    }

    private String allocationRemark(DmsOrderBalanceAllocation row) {
        return PRODUCT_COST.equals(row.getAllocationType()) ? "产品成本归集" : "剩余商品款归集";
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return nullToZero(value).setScale(2, RoundingMode.HALF_UP);
    }

    private record Target(DmsShopMember member, DmsAgent agent) {}

    private record AllocationAmounts(BigDecimal originalCost, BigDecimal currentCost,
                                     BigDecimal originalRemainder, BigDecimal currentRemainder) {}
}
