package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Set<String> INVOICE_STATES = Set.of("NOT_REQUIRED", "PENDING", "RECEIVED");

    private final DmsMerchantDao merchantDao;
    private final DmsMerchantAccountDao accountDao;
    private final DmsMerchantSettlementDao settlementDao;
    private final DmsMerchantWithdrawalDao withdrawalDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;

    @Override
    public List<DmsMerchant> listMerchants(String keyword, Integer status) {
        if (AdminContext.get() != null && AdminContext.get().getMerchantId() != null) {
            DmsMerchant own = merchantDao.selectById(AdminContext.get().getMerchantId());
            return own == null || (status != null && !status.equals(own.getStatus())) ? List.of() : List.of(own);
        }
        return merchantDao.selectList(tenantId(), trim(keyword), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant saveMerchant(DmsMerchant merchant) {
        requirePlatformAdmin();
        normalizeMerchant(merchant);
        if (merchantDao.selectByNo(merchant.getTenantId(), merchant.getMerchantNo()) != null) {
            Asserts.fail("商户编号已存在");
        }
        merchantDao.insert(merchant);
        DmsMerchantAccount account = new DmsMerchantAccount();
        account.setTenantId(merchant.getTenantId());
        account.setMerchantId(merchant.getId());
        accountDao.insert(account);
        return merchantDao.selectById(merchant.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant updateMerchant(Long id, DmsMerchant merchant) {
        requirePlatformAdmin();
        DmsMerchant existing = requireMerchant(id, false);
        merchant.setId(id);
        merchant.setTenantId(existing.getTenantId());
        merchant.setMerchantNo(existing.getMerchantNo());
        normalizeMerchant(merchant);
        merchantDao.update(merchant);
        return merchantDao.selectById(id);
    }

    @Override
    public boolean updateMerchantStatus(Long id, Integer status) {
        requirePlatformAdmin();
        requireMerchant(id, false);
        if (status == null || (status != 0 && status != 1)) Asserts.fail("商户状态不正确");
        return merchantDao.updateStatus(id, status) > 0;
    }

    @Override public List<DmsMerchantAccount> listAccounts(String keyword) { return accountDao.selectList(tenantId(), trim(keyword)); }
    @Override public List<DmsMerchantSettlement> listSettlements(Long merchantId, String status) {
        if (merchantId != null) requireMerchant(merchantId, false);
        return settlementDao.selectList(tenantId(), merchantId, upper(status));
    }
    @Override public List<DmsMerchantWithdrawal> listWithdrawals(Long merchantId, String status) {
        if (merchantId != null) requireMerchant(merchantId, false);
        return withdrawalDao.selectList(tenantId(), merchantId, upper(status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal applyWithdrawal(MerchantWithdrawalApplyDTO dto) {
        if (dto == null) Asserts.fail("提现申请不能为空");
        DmsMerchant merchant = requireMerchant(dto.getMerchantId(), true);
        BigDecimal amount = money(dto.getRequestedAmount());
        if (amount.compareTo(ZERO) <= 0) Asserts.fail("申请金额必须大于0");
        accountDao.selectByMerchantIdForUpdate(merchant.getId());
        if (accountDao.freezeAvailable(merchant.getId(), amount) != 1) Asserts.fail("商户可提现余额不足");
        DmsMerchantWithdrawal withdrawal = new DmsMerchantWithdrawal();
        withdrawal.setTenantId(merchant.getTenantId());
        withdrawal.setWithdrawalNo("MW" + IdUtil.getSnowflakeNextId());
        withdrawal.setMerchantId(merchant.getId());
        withdrawal.setRequestedAmount(amount);
        withdrawal.setInvoiceRequiredAmount(ZERO);
        withdrawal.setInvoiceReceivedAmount(ZERO);
        withdrawal.setInvoiceStatus("NOT_REQUIRED");
        withdrawal.setAdjustmentAmount(ZERO);
        withdrawal.setStatus("SUBMITTED");
        withdrawal.setApplyTime(LocalDateTime.now());
        withdrawalDao.insert(withdrawal);
        return withdrawalDao.selectById(withdrawal.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal reviewWithdrawal(Long id, MerchantWithdrawalReviewDTO dto) {
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("SUBMITTED", "INVOICE_PENDING", "READY_TO_PAY"));
        BigDecimal required = money(dto == null ? null : dto.getInvoiceRequiredAmount());
        BigDecimal received = money(dto == null ? null : dto.getInvoiceReceivedAmount());
        if (required.compareTo(ZERO) < 0 || received.compareTo(ZERO) < 0) Asserts.fail("发票金额不能小于0");
        String invoiceStatus = upper(dto == null ? null : dto.getInvoiceStatus());
        if (invoiceStatus == null) invoiceStatus = required.compareTo(ZERO) > 0 ? "PENDING" : "NOT_REQUIRED";
        if (!INVOICE_STATES.contains(invoiceStatus)) Asserts.fail("发票状态不正确");
        if ("RECEIVED".equals(invoiceStatus) && received.compareTo(required) < 0) Asserts.fail("已收票金额不能小于应开票金额");
        BigDecimal adjustment = money(dto == null ? null : dto.getAdjustmentAmount());
        BigDecimal expectedPaid = withdrawal.getRequestedAmount().add(adjustment);
        if (expectedPaid.compareTo(ZERO) <= 0 || expectedPaid.compareTo(withdrawal.getRequestedAmount()) > 0) {
            Asserts.fail("调整后的实际打款金额必须大于0且不能超过申请金额");
        }
        if (adjustment.compareTo(ZERO) != 0 && (dto == null || dto.getAdjustmentReason() == null || dto.getAdjustmentReason().isBlank())) {
            Asserts.fail("调整金额时必须填写原因");
        }
        withdrawal.setInvoiceRequiredAmount(required);
        withdrawal.setInvoiceReceivedAmount(received);
        withdrawal.setInvoiceStatus(invoiceStatus);
        withdrawal.setAdjustmentAmount(adjustment);
        withdrawal.setAdjustmentReason(trim(dto == null ? null : dto.getAdjustmentReason()));
        withdrawal.setStatus("PENDING".equals(invoiceStatus) ? "INVOICE_PENDING" : "READY_TO_PAY");
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal confirmPayment(Long id, MerchantWithdrawalPayDTO dto) {
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("READY_TO_PAY"));
        if ("PENDING".equals(withdrawal.getInvoiceStatus())) Asserts.fail("尚未收到约定发票，不能确认打款");
        BigDecimal expected = money(withdrawal.getRequestedAmount()).add(money(withdrawal.getAdjustmentAmount()));
        BigDecimal paid = money(dto == null ? null : dto.getActualPaidAmount());
        if (paid.compareTo(expected) != 0) Asserts.fail("实际打款金额必须与申请金额及调整金额一致");
        DmsMerchantAccount account = accountDao.selectByMerchantIdForUpdate(withdrawal.getMerchantId());
        BigDecimal requested = money(withdrawal.getRequestedAmount());
        BigDecimal adjustment = requested.subtract(paid);
        BigDecimal debt = money(account == null ? null : account.getDebtAmount());
        BigDecimal maximumPaidAfterDebt = requested.subtract(debt.min(requested)).max(ZERO);
        if (paid.compareTo(maximumPaidAfterDebt) > 0) {
            Asserts.fail("商户存在退款欠款，实际打款金额必须先扣除欠款并登记调整原因");
        }
        if (accountDao.settleFrozen(withdrawal.getMerchantId(), requested, paid, adjustment) != 1) {
            Asserts.fail("商户冻结余额不足，不能确认打款");
        }
        withdrawal.setActualPaidAmount(paid);
        withdrawal.setPaymentReference(trim(dto == null ? null : dto.getPaymentReference()));
        withdrawal.setPaymentVoucherUrl(trim(dto == null ? null : dto.getPaymentVoucherUrl()));
        withdrawal.setStatus("PAID");
        withdrawal.setPaidTime(LocalDateTime.now());
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal rejectWithdrawal(Long id, MerchantWithdrawalRejectDTO dto) {
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("SUBMITTED", "INVOICE_PENDING", "READY_TO_PAY"));
        String rejectReason = trim(dto == null ? null : dto.getReason());
        if (rejectReason == null) Asserts.fail("请填写驳回原因");
        accountDao.selectByMerchantIdForUpdate(withdrawal.getMerchantId());
        if (accountDao.unfreeze(withdrawal.getMerchantId(), money(withdrawal.getRequestedAmount())) != 1) {
            Asserts.fail("商户冻结余额异常，不能驳回");
        }
        withdrawal.setStatus("REJECTED");
        withdrawal.setRejectReason(rejectReason);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderSettlements(Long orderId) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null || order.getMerchantId() == null || !Set.of(1, 2, 3).contains(order.getStatus())) return;
        DmsMerchant merchant = requireMerchant(order.getMerchantId(), false);
        accountDao.selectByMerchantIdForUpdate(merchant.getId());
        for (DmsShopOrderItem item : orderItemDao.selectByOrderId(orderId)) {
            if (item.getMerchantId() == null || !merchant.getId().equals(item.getMerchantId())) continue;
            if (settlementDao.selectByOrderItemIdForUpdate(item.getId()) != null) continue;
            BigDecimal amount = money(item.getCostAmount()).multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(ZERO) <= 0) continue;
            DmsMerchantSettlement settlement = new DmsMerchantSettlement();
            settlement.setTenantId(order.getTenantId());
            settlement.setMerchantId(merchant.getId());
            settlement.setOrderId(orderId);
            settlement.setOrderNo(order.getOrderNo());
            settlement.setOrderItemId(item.getId());
            settlement.setProductId(item.getProductId());
            settlement.setSkuId(item.getSkuId());
            settlement.setQuantity(item.getQuantity());
            settlement.setCostAmount(money(item.getCostAmount()));
            settlement.setSettlementAmount(amount);
            settlement.setStatus("PENDING");
            settlementDao.insert(settlement);
            if (accountDao.addPending(merchant.getId(), amount) != 1) Asserts.fail("商户待结算余额入账失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseEligibleSettlements(int limit) {
        int released = 0;
        for (Long orderId : settlementDao.selectPendingOrderIds(Math.max(1, Math.min(500, limit)))) {
            DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
            if (order == null || !Integer.valueOf(3).equals(order.getStatus())
                    || !afterSaleWindowPolicy.isExpired(order, LocalDateTime.now())
                    || afterSaleDao.selectOpenByOrderId(orderId) != null) continue;
            for (DmsMerchantSettlement settlement : settlementDao.selectByOrderId(orderId)) {
                if (!"PENDING".equals(settlement.getStatus())) continue;
                DmsMerchantSettlement locked = settlementDao.selectByIdForUpdate(settlement.getId());
                BigDecimal net = money(locked.getSettlementAmount()).subtract(money(locked.getReversedAmount()));
                accountDao.selectByMerchantIdForUpdate(locked.getMerchantId());
                if (net.compareTo(ZERO) > 0 && accountDao.releasePending(locked.getMerchantId(), net) != 1) {
                    Asserts.fail("商户待结算余额释放失败");
                }
                if (settlementDao.markAvailable(locked.getId()) == 1) released++;
            }
        }
        return released;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseAfterSaleItems(List<DmsShopAfterSaleItem> items) {
        for (DmsShopAfterSaleItem item : items == null ? List.<DmsShopAfterSaleItem>of() : items) {
            DmsMerchantSettlement settlement = settlementDao.selectByOrderItemIdForUpdate(item.getOrderItemId());
            if (settlement == null) continue;
            int quantity = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
            if (quantity <= 0 || quantity > settlement.getQuantity() - settlement.getRefundedQuantity()) {
                Asserts.fail("商户货款退款数量异常");
            }
            BigDecimal amount = money(settlement.getCostAmount()).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            accountDao.selectByMerchantIdForUpdate(settlement.getMerchantId());
            if ("PENDING".equals(settlement.getStatus())) {
                if (accountDao.reversePending(settlement.getMerchantId(), amount) != 1) Asserts.fail("商户待结算货款冲回失败");
            } else if ("AVAILABLE".equals(settlement.getStatus())) {
                if (accountDao.reverseAvailableOrCreateDebt(settlement.getMerchantId(), amount) != 1) Asserts.fail("商户可用货款冲回失败");
            } else {
                Asserts.fail("商户货款当前状态不能冲回");
            }
            boolean full = settlement.getRefundedQuantity() + quantity >= settlement.getQuantity();
            if (settlementDao.applyReversal(settlement.getId(), quantity, amount, full ? "REVERSED" : settlement.getStatus()) != 1) {
                Asserts.fail("商户货款冲回记录更新失败");
            }
        }
    }

    private void normalizeMerchant(DmsMerchant merchant) {
        if (merchant == null || merchant.getMerchantName() == null || merchant.getMerchantName().isBlank()) Asserts.fail("商户名称不能为空");
        merchant.setTenantId(tenantId());
        merchant.setMerchantNo(merchant.getMerchantNo() == null || merchant.getMerchantNo().isBlank()
                ? "M" + IdUtil.getSnowflakeNextId() : merchant.getMerchantNo().trim().toUpperCase(Locale.ROOT));
        merchant.setMerchantName(merchant.getMerchantName().trim());
        merchant.setContactName(trim(merchant.getContactName()));
        merchant.setContactPhone(trim(merchant.getContactPhone()));
        merchant.setSettlementMode("COST_PRICE");
        merchant.setStatus(merchant.getStatus() == null ? 1 : merchant.getStatus());
        merchant.setRemark(trim(merchant.getRemark()));
    }

    private DmsMerchant requireMerchant(Long id, boolean active) {
        DmsMerchant merchant = id == null ? null : merchantDao.selectById(id);
        if (merchant == null || !tenantId().equals(merchant.getTenantId())) Asserts.fail("商户不存在");
        if (active && !Integer.valueOf(1).equals(merchant.getStatus())) Asserts.fail("商户已停用");
        return merchant;
    }

    private DmsMerchantWithdrawal requireWithdrawalForUpdate(Long id, Set<String> states) {
        DmsMerchantWithdrawal withdrawal = withdrawalDao.selectByIdForUpdate(id);
        if (withdrawal == null || !tenantId().equals(withdrawal.getTenantId())) Asserts.fail("商户提现申请不存在");
        if (!states.contains(withdrawal.getStatus())) Asserts.fail("当前提现状态不能执行该操作");
        return withdrawal;
    }

    private void applyOperator(DmsMerchantWithdrawal withdrawal) {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null) return;
        withdrawal.setOperatorId(admin.getId());
        withdrawal.setOperatorName(admin.getNickname() == null || admin.getNickname().isBlank() ? admin.getUsername() : admin.getNickname());
    }

    private Long tenantId() { return TenantContext.getTenantId(); }
    private void requirePlatformAdmin() {
        if (AdminContext.get() != null && AdminContext.get().getMerchantId() != null) Asserts.fail("商户工作台账号不能维护商户资料");
    }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { String text = trim(value); return text == null ? null : text.toUpperCase(Locale.ROOT); }
}
