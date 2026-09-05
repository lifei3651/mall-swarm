package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.security.TemporaryAdminCredential;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.MerchantBalanceReconciliationVO;
import com.macro.mall.distribution.vo.MerchantExitReadinessVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantServiceImpl implements MerchantService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Set<String> INVOICE_STATES = Set.of("NOT_REQUIRED", "PENDING", "RECEIVED");
    private static final Set<String> CONTRACT_STATES = Set.of("PENDING", "SIGNED", "EXPIRED");
    private static final Set<String> ACCOUNT_STATES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> BUSINESS_STATES = Set.of("ACTIVE", "SUSPENDED", "CLOSED");
    private static final Set<String> FULFILLMENT_STATES = Set.of("ENABLED", "PLATFORM_ONLY", "DISABLED");
    private static final Set<String> BINARY_CONTROL_STATES = Set.of("ENABLED", "FROZEN");
    private static final Set<String> DEPOSIT_STATES = Set.of("NORMAL", "FROZEN");
    private static final Set<String> AUDIT_STATES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> EXIT_STATES = Set.of("NORMAL", "EXITING", "EXITED");

    private final DmsMerchantDao merchantDao;
    private final DmsAdminUserDao adminUserDao;
    private final DmsMerchantAccountDao accountDao;
    private final DmsMerchantSettlementDao settlementDao;
    private final DmsMerchantWithdrawalDao withdrawalDao;
    private final DmsMerchantDepositFlowDao depositFlowDao;
    private final DmsMerchantLedgerDao ledgerDao;
    private final DmsMerchantWithdrawalEventDao withdrawalEventDao;
    private final DmsMerchantProductReviewDao merchantProductReviewDao;
    private final DmsShopOrderDao orderDao;
    private final DmsShopOrderItemDao orderItemDao;
    private final DmsShopAfterSaleDao afterSaleDao;
    private final DmsShopProductDao productDao;
    private final ShopAfterSaleWindowPolicy afterSaleWindowPolicy;
    private final com.macro.mall.distribution.service.ShopCatalogCacheService catalogCache;
    private final com.macro.mall.distribution.service.OperationLogService operationLogService;
    private final AdminAuthService adminAuthService;
    private final TransactionTemplate transactionTemplate;

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
        recordOpeningLedger(merchant);
        return merchantDao.selectById(merchant.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant onboardMerchant(MerchantOnboardingDTO dto) {
        DmsAdminUser actor = requirePlatformActorAndVerify(dto == null ? null : dto.getCurrentAdminPassword());
        if (dto == null) Asserts.fail("商户开通信息不能为空");
        String username = trim(dto.getUsername());
        if (username == null || !username.matches("^[A-Za-z][A-Za-z0-9_]{3,31}$")) {
            Asserts.fail("商家账号需为4至32位，必须以英文字母开头且仅支持字母、数字和下划线");
        }
        if (adminUserDao.selectByUsername(username) != null) Asserts.fail("商家账号已存在");

        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo(dto.getMerchantNo());
        merchant.setMerchantName(dto.getMerchantName());
        merchant.setContactName(dto.getContactName());
        merchant.setContactPhone(dto.getContactPhone());
        merchant.setRequiredDepositAmount(dto.getRequiredDepositAmount());
        merchant.setDefaultSettlementDays(dto.getDefaultSettlementDays());
        merchant.setAccountStatus("ENABLED");
        merchant.setBusinessStatus("SUSPENDED");
        merchant.setFulfillmentStatus("ENABLED");
        merchant.setWithdrawalStatus("FROZEN");
        merchant.setSettlementStatus("FROZEN");
        merchant.setDepositStatus("NORMAL");
        merchant.setAuditStatus("PENDING");
        merchant.setExitStatus("NORMAL");
        merchant.setStatus(0);
        normalizeMerchant(merchant);
        if (merchantDao.selectByNo(merchant.getTenantId(), merchant.getMerchantNo()) != null) Asserts.fail("商户编号已存在");
        merchantDao.insert(merchant);

        DmsMerchantAccount account = new DmsMerchantAccount();
        account.setTenantId(merchant.getTenantId());
        account.setMerchantId(merchant.getId());
        accountDao.insert(account);
        recordOpeningLedger(merchant);

        DmsAdminUser merchantAdmin = new DmsAdminUser();
        String temporaryPassword = TemporaryAdminCredential.generate();
        LocalDateTime credentialExpiresAt = TemporaryAdminCredential.expiresAt();
        merchantAdmin.setUsername(username);
        merchantAdmin.setPasswordHash(BCrypt.hashpw(temporaryPassword));
        merchantAdmin.setSalt("BCRYPT");
        merchantAdmin.setNickname(trim(dto.getContactName()) == null ? merchant.getMerchantName() : trim(dto.getContactName()));
        merchantAdmin.setRoleCode("MERCHANT_OWNER");
        merchantAdmin.setPermissions("admin:read,shop:product,shop:order,shop:aftersale,finance:read,finance:manage,merchant:staff-manage");
        merchantAdmin.setMerchantId(merchant.getId());
        merchantAdmin.setStatus(1);
        merchantAdmin.setMustChangePassword(1);
        merchantAdmin.setCredentialExpiresAt(credentialExpiresAt);
        adminUserDao.insert(merchantAdmin);
        operationLogService.log("MERCHANT", "ONBOARD", "MERCHANT", String.valueOf(merchant.getId()),
                null, "merchant=" + merchant.getMerchantNo() + ";account=" + username + ";audit=PENDING",
                "开通商户工作台，等待商户提交经营与结算资料；操作人=" + actor.getUsername());
        DmsMerchant result = merchantDao.selectById(merchant.getId());
        result.setOnboardingUsername(username);
        result.setTemporaryPassword(temporaryPassword);
        result.setCredentialExpiresAt(credentialExpiresAt);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant updateMerchant(Long id, DmsMerchant merchant) {
        requirePlatformAdmin();
        DmsMerchant existing = requireMerchantForUpdate(id, false);
        Integer previousSettlementDays = existing.getDefaultSettlementDays();
        merchant.setId(id);
        merchant.setTenantId(existing.getTenantId());
        merchant.setMerchantNo(existing.getMerchantNo());
        normalizeMerchant(merchant);
        boolean settlementDaysChanged = !java.util.Objects.equals(previousSettlementDays, merchant.getDefaultSettlementDays());
        merchantDao.update(merchant);
        if (settlementDaysChanged) {
            String reason = "商户默认结算等待已调整，请核对新周期后重新提交审核";
            merchantProductReviewDao.rejectPendingByMerchant(existing.getTenantId(), id, reason);
            productDao.resetDefaultSettlementProductsForReview(existing.getTenantId(), id,
                    reason);
            catalogCache.invalidateAfterCommit(existing.getTenantId());
        }
        operationLogService.log("MERCHANT", "PROFILE_UPDATE", "MERCHANT", String.valueOf(id),
                merchantProfileSummary(existing), merchantProfileSummary(merchant),
                "修改商户经营主体、收款资料、保证金要求或默认结算设置");
        return merchantDao.selectById(id);
    }

    @Override
    public DmsMerchant currentMerchantProfile() {
        Long merchantId = currentMerchantId();
        if (merchantId == null) Asserts.fail("仅商户工作台账号可以查看入驻资料");
        return requireMerchant(merchantId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant submitCurrentMerchantProfile(MerchantProfileSubmitDTO dto) {
        Long merchantId = currentMerchantId();
        if (merchantId == null) Asserts.fail("仅商户工作台账号可以提交入驻资料");
        DmsMerchant existing = requireMerchantForUpdate(merchantId, false);
        if (dto == null) Asserts.fail("入驻资料不能为空");
        existing.setContactName(trim(dto.getContactName()));
        existing.setContactPhone(trim(dto.getContactPhone()));
        existing.setLegalEntityName(trim(dto.getLegalEntityName()));
        existing.setUnifiedSocialCreditCode(upper(dto.getUnifiedSocialCreditCode()));
        existing.setBankAccountName(trim(dto.getBankAccountName()));
        existing.setBankName(trim(dto.getBankName()));
        existing.setBankAccountNo(trim(dto.getBankAccountNo()));
        existing.setInvoiceTitle(trim(dto.getInvoiceTitle()));
        existing.setTaxpayerIdentificationNo(upper(dto.getTaxpayerIdentificationNo()));
        validateMerchantProfile(existing);
        if (merchantDao.submitProfile(existing) != 1) Asserts.fail("提交商户入驻资料失败");
        merchantProductReviewDao.rejectPendingByMerchant(existing.getTenantId(), existing.getId(), "商户准入资料已更新，请在认证通过后重新提交商品审核");
        productDao.disableByMerchantId(existing.getTenantId(), existing.getId());
        catalogCache.invalidateAfterCommit(existing.getTenantId());
        operationLogService.log("MERCHANT", "PROFILE_SUBMIT", "MERCHANT", String.valueOf(existing.getId()),
                null, merchantProfileSummary(existing), "商户提交经营主体、收款与开票资料，等待平台认证");
        return merchantDao.selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMerchantStatus(Long id, Integer status) {
        requirePlatformAdmin();
        DmsMerchant merchant = requireMerchantForUpdate(id, false);
        if (status == null || (status != 0 && status != 1)) Asserts.fail("商户状态不正确");
        MerchantControlDTO control = controlsOf(merchant);
        control.setBusinessStatus(status == 1 ? "ACTIVE" : "SUSPENDED");
        control.setWithdrawalStatus(status == 1 ? "ENABLED" : "FROZEN");
        control.setSettlementStatus(status == 1 ? "ENABLED" : "FROZEN");
        control.setFulfillmentStatus("ENABLED");
        control.setReason(status == 1 ? "兼容入口恢复经营" : "兼容入口暂停新销售，保留历史订单履约");
        int compatibilityStatus = "ACTIVE".equals(control.getBusinessStatus())
                && "APPROVED".equals(control.getAuditStatus()) && "NORMAL".equals(control.getExitStatus()) ? 1 : 0;
        boolean updated = merchantDao.updateControls(id, compatibilityStatus, control) > 0;
        if (updated && compatibilityStatus == 0) productDao.disableByMerchantId(merchant.getTenantId(), id);
        if (updated) catalogCache.invalidateAfterCommit(merchant.getTenantId());
        if (updated) {
            operationLogService.log("MERCHANT", "CONTROL_UPDATE_COMPAT", "MERCHANT", String.valueOf(id),
                    controlSummary(merchant), controlSummary(control), control.getReason());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchant updateMerchantControls(Long id, MerchantControlDTO dto) {
        requirePlatformAdmin();
        DmsMerchant merchant = requireMerchantForUpdate(id, false);
        normalizeControls(dto);
        if ("APPROVED".equals(dto.getAuditStatus()) && !"APPROVED".equals(merchant.getAuditStatus())) {
            if (!"PENDING".equals(merchant.getAuditStatus())) Asserts.fail("商户需要重新提交入驻资料后才能认证通过");
            validateMerchantProfile(merchant);
            // 平台认证是商户恢复经营的准入动作；商品仍需逐个提交平台审核后才能上架。
            dto.setBusinessStatus("ACTIVE");
            dto.setWithdrawalStatus("ENABLED");
            dto.setSettlementStatus("ENABLED");
        }
        validateExitTransition(merchant, dto);
        int compatibilityStatus = "ACTIVE".equals(dto.getBusinessStatus())
                && "APPROVED".equals(dto.getAuditStatus()) && "NORMAL".equals(dto.getExitStatus()) ? 1 : 0;
        if (merchantDao.updateControls(id, compatibilityStatus, dto) != 1) Asserts.fail("商户控制状态更新失败");
        if (compatibilityStatus == 0) productDao.disableByMerchantId(merchant.getTenantId(), id);
        catalogCache.invalidateAfterCommit(merchant.getTenantId());
        operationLogService.log("MERCHANT", "CONTROL_UPDATE", "MERCHANT", String.valueOf(id),
                controlSummary(merchant), controlSummary(dto), "调整商户业务能力，原因：" + dto.getReason().trim());
        return merchantDao.selectById(id);
    }

    @Override
    public MerchantExitReadinessVO getExitReadiness(Long id) {
        requirePlatformAdmin();
        requireMerchant(id, false);
        MerchantExitReadinessVO readiness = merchantDao.selectExitReadiness(tenantId(), id);
        if (readiness == null) Asserts.fail("商户退出检查失败");
        List<String> blockers = new ArrayList<>();
        addCountBlocker(blockers, readiness.getActiveProductCount(), "仍有上架商品");
        addCountBlocker(blockers, readiness.getUnfinishedOrderCount(), "仍有待付款、待发货或待收货订单");
        addCountBlocker(blockers, readiness.getOpenAfterSaleCount(), "仍有处理中售后");
        addCountBlocker(blockers, readiness.getPendingSettlementCount(), "仍有待结算货款");
        addCountBlocker(blockers, readiness.getActiveWithdrawalCount(), "仍有未结束提现单");
        addMoneyBlocker(blockers, readiness.getPendingAmount(), "待结算余额");
        addMoneyBlocker(blockers, readiness.getAvailableAmount(), "可提现余额");
        addMoneyBlocker(blockers, readiness.getFrozenAmount(), "提现冻结余额");
        addMoneyBlocker(blockers, readiness.getDepositAmount(), "未释放保证金");
        addMoneyBlocker(blockers, readiness.getDebtAmount(), "退款欠款");
        readiness.setBlockers(blockers);
        readiness.setReady(blockers.isEmpty());
        return readiness;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assertOrderCanBePaid(Long orderId) {
        DmsShopOrder order = orderDao.selectById(orderId);
        if (order == null || order.getMerchantId() == null) return;
        // 与商户停业/清退操作锁同一商户行，避免支付校验通过后并发进入已停业状态。
        DmsMerchant merchant = requireMerchantForUpdate(order.getMerchantId(), false);
        if (!"ACTIVE".equals(merchant.getBusinessStatus()) || !"APPROVED".equals(merchant.getAuditStatus())
                || !"NORMAL".equals(merchant.getExitStatus())) {
            Asserts.fail("商品所属商户已暂停新销售，该待付款订单不能继续支付，请重新下单");
        }
    }

    @Override public List<DmsMerchantAccount> listAccounts(String keyword) {
        Long merchantId = currentMerchantId();
        if (merchantId == null) return accountDao.selectList(tenantId(), trim(keyword));
        DmsMerchantAccount account = accountDao.selectByMerchantId(merchantId);
        return account == null ? List.of() : List.of(account);
    }
    @Override public List<DmsMerchantSettlement> listSettlements(Long merchantId, String status) {
        merchantId = resolveMerchantScope(merchantId);
        if (merchantId != null) requireMerchant(merchantId, false);
        List<DmsMerchantSettlement> rows = settlementDao.selectList(tenantId(), merchantId, upper(status));
        rows.forEach(row -> {
            if (row.getEligibleTime() == null) {
                DmsShopOrder order = orderDao.selectById(row.getOrderId());
                row.setEligibleTime(settlementEligibleTime(order, row.getSettlementDelayDays()));
            }
        });
        return rows;
    }
    @Override public List<DmsMerchantWithdrawal> listWithdrawals(Long merchantId, String status) {
        merchantId = resolveMerchantScope(merchantId);
        if (merchantId != null) requireMerchant(merchantId, false);
        return withdrawalDao.selectList(tenantId(), merchantId, upper(status));
    }

    @Override public List<DmsMerchantWithdrawalEvent> listWithdrawalEvents(Long withdrawalId) {
        DmsMerchantWithdrawal withdrawal = withdrawalDao.selectById(withdrawalId);
        if (withdrawal == null || !tenantId().equals(withdrawal.getTenantId())) Asserts.fail("商户提现申请不存在");
        Long current = currentMerchantId();
        if (current != null && !current.equals(withdrawal.getMerchantId())) Asserts.fail("不能访问其他商户的提现记录");
        return withdrawalEventDao.selectByWithdrawalId(withdrawalId);
    }

    @Override public List<DmsMerchantDepositFlow> listDepositFlows(Long merchantId) {
        merchantId = resolveMerchantScope(merchantId);
        if (merchantId != null) requireMerchant(merchantId, false);
        return depositFlowDao.selectList(tenantId(), merchantId);
    }

    @Override public List<DmsMerchantLedger> listLedgers(Long merchantId, String bizType) {
        merchantId = resolveMerchantScope(merchantId);
        if (merchantId != null) requireMerchant(merchantId, false);
        return ledgerDao.selectList(tenantId(), merchantId, upper(bizType));
    }

    @Override
    public List<MerchantBalanceReconciliationVO> reconcileBalances(Long merchantId) {
        merchantId = resolveMerchantScope(merchantId);
        if (merchantId != null) requireMerchant(merchantId, false);
        List<DmsMerchantAccount> accounts;
        if (merchantId == null) {
            accounts = accountDao.selectList(tenantId(), null);
        } else {
            DmsMerchantAccount own = accountDao.selectByMerchantId(merchantId);
            accounts = own == null ? List.of() : List.of(own);
        }
        Map<Long, DmsMerchantLedger> latestByMerchant = new HashMap<>();
        for (DmsMerchantLedger row : ledgerDao.selectLatestList(tenantId(), merchantId)) {
            latestByMerchant.put(row.getMerchantId(), row);
        }
        List<MerchantBalanceReconciliationVO> result = new ArrayList<>();
        for (DmsMerchantAccount account : accounts) {
            if (account == null) continue;
            DmsMerchantLedger ledger = latestByMerchant.get(account.getMerchantId());
            MerchantBalanceReconciliationVO row = new MerchantBalanceReconciliationVO();
            row.setMerchantId(account.getMerchantId());
            row.setMerchantName(account.getMerchantName());
            row.setLedgerInitialized(ledger != null);
            row.setLatestLedgerNo(ledger == null ? null : ledger.getLedgerNo());
            row.setLatestLedgerTime(ledger == null ? null : ledger.getCreateTime());
            row.setPendingAmount(money(account.getPendingAmount()));
            row.setAvailableAmount(money(account.getAvailableAmount()));
            row.setFrozenAmount(money(account.getFrozenAmount()));
            row.setDepositAmount(money(account.getDepositFrozenAmount()));
            row.setDebtAmount(money(account.getDebtAmount()));
            row.setPaidAmount(money(account.getTotalPaidAmount()));
            row.setPendingDifference(difference(account.getPendingAmount(), ledger == null ? null : ledger.getPendingAfter()));
            row.setAvailableDifference(difference(account.getAvailableAmount(), ledger == null ? null : ledger.getAvailableAfter()));
            row.setFrozenDifference(difference(account.getFrozenAmount(), ledger == null ? null : ledger.getFrozenAfter()));
            row.setDepositDifference(difference(account.getDepositFrozenAmount(), ledger == null ? null : ledger.getDepositAfter()));
            row.setDebtDifference(difference(account.getDebtAmount(), ledger == null ? null : ledger.getDebtAfter()));
            row.setPaidDifference(difference(account.getTotalPaidAmount(), ledger == null ? null : ledger.getPaidAfter()));
            row.setConsistent(ledger != null && allZero(row.getPendingDifference(), row.getAvailableDifference(),
                    row.getFrozenDifference(), row.getDepositDifference(), row.getDebtDifference(), row.getPaidDifference()));
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantDepositFlow freezeDeposit(MerchantDepositAdjustDTO dto) {
        return adjustDeposit(dto, "FREEZE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantDepositFlow receiveDeposit(MerchantDepositAdjustDTO dto) {
        return adjustDeposit(dto, "RECEIVE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantDepositFlow releaseDeposit(MerchantDepositAdjustDTO dto) {
        return adjustDeposit(dto, "RELEASE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal applyWithdrawal(MerchantWithdrawalApplyDTO dto) {
        if (dto == null) Asserts.fail("提现申请不能为空");
        Long merchantId = currentMerchantId();
        if (merchantId != null) dto.setMerchantId(merchantId);
        DmsMerchant merchant = requireMerchant(dto.getMerchantId(), false);
        BigDecimal amount = money(dto.getRequestedAmount());
        if (amount.compareTo(ZERO) <= 0) Asserts.fail("申请金额必须大于0");
        String requestNo = trim(dto.getRequestNo());
        if (requestNo == null) Asserts.fail("缺少提现申请请求号");
        if (requestNo.length() > 64 || !requestNo.matches("[A-Za-z0-9._:-]+")) {
            Asserts.fail("提现申请请求号格式不正确");
        }
        DmsMerchantAccount account = accountDao.selectByMerchantIdForUpdate(merchant.getId());
        DmsMerchantWithdrawal replay = withdrawalDao.selectByRequestNo(merchant.getTenantId(), merchant.getId(), requestNo);
        if (replay != null) {
            if (money(replay.getRequestedAmount()).compareTo(amount) != 0) Asserts.fail("提现请求号已被其他金额使用");
            return replay;
        }
        if (account == null) Asserts.fail("商户货款账户不存在");
        if (!"ENABLED".equals(merchant.getWithdrawalStatus())) Asserts.fail("商户提现已被冻结");
        if (!"NORMAL".equals(merchant.getDepositStatus())) Asserts.fail("商户保证金账户已被冻结");
        requirePayoutProfile(merchant);
        if (money(account.getDepositFrozenAmount()).compareTo(money(merchant.getRequiredDepositAmount())) < 0) {
            Asserts.fail("商户保证金未达到平台要求，暂不能提现");
        }
        if (accountDao.freezeAvailable(merchant.getId(), amount) != 1) Asserts.fail("商户可提现余额不足");
        DmsMerchantWithdrawal withdrawal = new DmsMerchantWithdrawal();
        withdrawal.setTenantId(merchant.getTenantId());
        withdrawal.setWithdrawalNo("MW" + IdUtil.getSnowflakeNextId());
        withdrawal.setRequestNo(requestNo);
        withdrawal.setMerchantId(merchant.getId());
        withdrawal.setMerchantProfileVersion(merchant.getProfileVersion());
        withdrawal.setLegalEntityNameSnapshot(merchant.getLegalEntityName());
        withdrawal.setBankAccountNameSnapshot(merchant.getBankAccountName());
        withdrawal.setBankNameSnapshot(merchant.getBankName());
        withdrawal.setBankAccountNoSnapshot(merchant.getBankAccountNo());
        withdrawal.setRequestedAmount(amount);
        withdrawal.setInvoiceRequiredAmount(ZERO);
        withdrawal.setInvoiceReceivedAmount(ZERO);
        withdrawal.setInvoiceStatus("NOT_REQUIRED");
        withdrawal.setAdjustmentAmount(ZERO);
        withdrawal.setStatus("SUBMITTED");
        withdrawal.setApplyTime(LocalDateTime.now());
        withdrawalDao.insert(withdrawal);
        recordWithdrawalEvent(withdrawal, null, "SUBMITTED", "商户提交提现申请");
        recordLedger(merchant, account, "WITHDRAWAL_APPLY", withdrawal.getWithdrawalNo(), "提交提现申请并冻结可提现余额");
        return withdrawalDao.selectById(withdrawal.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal reviewWithdrawal(Long id, MerchantWithdrawalReviewDTO dto) {
        requirePlatformAdmin();
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
        String previousStatus = withdrawal.getStatus();
        withdrawal.setInvoiceRequiredAmount(required);
        withdrawal.setInvoiceReceivedAmount(received);
        withdrawal.setInvoiceStatus(invoiceStatus);
        withdrawal.setAdjustmentAmount(adjustment);
        withdrawal.setAdjustmentReason(trim(dto == null ? null : dto.getAdjustmentReason()));
        withdrawal.setStatus("PENDING".equals(invoiceStatus) ? "INVOICE_PENDING" : "READY_TO_PAY");
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, previousStatus, withdrawal.getStatus(),
                "财务审核完成；发票状态=" + invoiceStatus + (withdrawal.getAdjustmentReason() == null ? "" : "；调整原因=" + withdrawal.getAdjustmentReason()));
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal confirmPayment(Long id, MerchantWithdrawalPayDTO dto) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("READY_TO_PAY", "PAYMENT_PROCESSING"));
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
        String previousStatus = withdrawal.getStatus();
        if ("READY_TO_PAY".equals(previousStatus)) {
            withdrawal.setStatus("PAYMENT_PROCESSING");
            applyOperator(withdrawal);
            withdrawalDao.update(withdrawal);
            recordWithdrawalEvent(withdrawal, "READY_TO_PAY", "PAYMENT_PROCESSING", "财务开始付款处理");
            previousStatus = "PAYMENT_PROCESSING";
        }
        withdrawal.setActualPaidAmount(paid);
        withdrawal.setPaymentReference(trim(dto == null ? null : dto.getPaymentReference()));
        withdrawal.setPaymentVoucherUrl(trim(dto == null ? null : dto.getPaymentVoucherUrl()));
        withdrawal.setStatus("PAID");
        withdrawal.setPaidTime(LocalDateTime.now());
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        String paymentReference = trim(withdrawal.getPaymentReference());
        recordWithdrawalEvent(withdrawal, previousStatus, "PAID",
                "财务确认付款" + (paymentReference == null ? "" : "；银行流水号=" + paymentReference));
        recordLedger(requireMerchant(withdrawal.getMerchantId(), false), account, "WITHDRAWAL_PAID",
                withdrawal.getWithdrawalNo(), "提现付款完成");
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal startWithdrawalPayment(Long id) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("READY_TO_PAY", "PAYMENT_FAILED"));
        if ("PENDING".equals(withdrawal.getInvoiceStatus())) Asserts.fail("尚未收到约定发票，不能开始付款");
        String previous = withdrawal.getStatus();
        withdrawal.setStatus("PAYMENT_PROCESSING");
        withdrawal.setRejectReason(null);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, previous, "PAYMENT_PROCESSING",
                "PAYMENT_FAILED".equals(previous) ? "财务重新发起付款" : "财务开始付款处理");
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal markWithdrawalPaymentFailed(Long id, MerchantWithdrawalActionDTO dto) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("PAYMENT_PROCESSING"));
        String reason = actionReason(dto, "请填写付款失败原因");
        withdrawal.setStatus("PAYMENT_FAILED");
        withdrawal.setRejectReason(reason);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, "PAYMENT_PROCESSING", "PAYMENT_FAILED", "付款失败：" + reason);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal rejectWithdrawal(Long id, MerchantWithdrawalRejectDTO dto) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("SUBMITTED", "INVOICE_PENDING", "READY_TO_PAY", "PAYMENT_FAILED"));
        String rejectReason = trim(dto == null ? null : dto.getReason());
        if (rejectReason == null) Asserts.fail("请填写驳回原因");
        DmsMerchantAccount before = accountDao.selectByMerchantIdForUpdate(withdrawal.getMerchantId());
        if (accountDao.unfreeze(withdrawal.getMerchantId(), money(withdrawal.getRequestedAmount())) != 1) {
            Asserts.fail("商户冻结余额异常，不能驳回");
        }
        String previousStatus = withdrawal.getStatus();
        withdrawal.setStatus("REJECTED");
        withdrawal.setRejectReason(rejectReason);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, previousStatus, "REJECTED", "财务驳回：" + rejectReason);
        recordLedger(requireMerchant(withdrawal.getMerchantId(), false), before, "WITHDRAWAL_REJECT",
                withdrawal.getWithdrawalNo(), "提现驳回并退回冻结金额");
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal cancelWithdrawal(Long id, MerchantWithdrawalActionDTO dto) {
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("SUBMITTED", "INVOICE_PENDING"));
        Long merchantId = currentMerchantId();
        if (merchantId != null && !merchantId.equals(withdrawal.getMerchantId())) Asserts.fail("不能撤回其他商户的提现申请");
        String reason = actionReason(dto, "请填写撤回原因");
        DmsMerchantAccount before = accountDao.selectByMerchantIdForUpdate(withdrawal.getMerchantId());
        if (accountDao.unfreeze(withdrawal.getMerchantId(), money(withdrawal.getRequestedAmount())) != 1) {
            Asserts.fail("商户冻结余额异常，不能撤回");
        }
        String previous = withdrawal.getStatus();
        withdrawal.setStatus("CANCELED");
        withdrawal.setRejectReason(reason);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, previous, "CANCELED", "提现申请撤回：" + reason);
        recordLedger(requireMerchant(withdrawal.getMerchantId(), false), before, "WITHDRAWAL_CANCEL",
                withdrawal.getWithdrawalNo(), "提现撤回并退回冻结金额");
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal riskFreezeWithdrawal(Long id, MerchantWithdrawalActionDTO dto) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id,
                Set.of("SUBMITTED", "INVOICE_PENDING", "READY_TO_PAY", "PAYMENT_PROCESSING", "PAYMENT_FAILED"));
        String reason = actionReason(dto, "请填写风控冻结原因");
        String previous = withdrawal.getStatus();
        withdrawal.setResumeStatus(previous);
        withdrawal.setStatus("RISK_FROZEN");
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, previous, "RISK_FROZEN", "风控冻结：" + reason);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal resumeWithdrawal(Long id, MerchantWithdrawalActionDTO dto) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("RISK_FROZEN"));
        String reason = actionReason(dto, "请填写解除冻结原因");
        String resumeStatus = upper(withdrawal.getResumeStatus());
        if (resumeStatus == null || !Set.of("SUBMITTED", "INVOICE_PENDING", "READY_TO_PAY", "PAYMENT_PROCESSING", "PAYMENT_FAILED").contains(resumeStatus)) {
            Asserts.fail("提现冻结前状态缺失，不能自动恢复，请联系技术人员核对审批轨迹");
        }
        withdrawal.setStatus(resumeStatus);
        withdrawal.setResumeStatus(null);
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, "RISK_FROZEN", resumeStatus, "解除风控冻结：" + reason);
        return withdrawalDao.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsMerchantWithdrawal completeWithdrawal(Long id) {
        requirePlatformAdmin();
        DmsMerchantWithdrawal withdrawal = requireWithdrawalForUpdate(id, Set.of("PAID"));
        withdrawal.setStatus("COMPLETED");
        applyOperator(withdrawal);
        withdrawalDao.update(withdrawal);
        recordWithdrawalEvent(withdrawal, "PAID", "COMPLETED", "财务确认单据归档完成");
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
            settlement.setSettlementDelayDays(normalizeSettlementDays(item.getSettlementDelayDays()));
            settlement.setStatus("PENDING");
            settlementDao.insert(settlement);
            DmsMerchantAccount before = accountDao.selectByMerchantId(merchant.getId());
            if (accountDao.addPending(merchant.getId(), amount) != 1) Asserts.fail("商户待结算余额入账失败");
            recordLedger(merchant, before, "ORDER_PENDING", String.valueOf(settlement.getId()),
                    "订单货款进入待结算：" + order.getOrderNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockOrderSettlementEligibility(Long orderId) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null || order.getReceiveTime() == null) return;
        for (DmsMerchantSettlement settlement : settlementDao.selectByOrderId(orderId)) {
            if (!"PENDING".equals(settlement.getStatus()) || settlement.getEligibleTime() != null) continue;
            LocalDateTime eligibleTime = settlementEligibleTime(order, settlement.getSettlementDelayDays());
            if (eligibleTime != null) settlementDao.updateEligibleTime(settlement.getId(), eligibleTime);
        }
    }

    @Override
    public int releaseEligibleSettlements(int limit) {
        int batchSize = Math.max(1, Math.min(500, limit));
        Long currentTenantId = tenantId();
        // 兼容迁移前已确认收货的待结算记录；新订单会在确认收货事务内直接固化。
        for (Long orderId : settlementDao.selectPendingOrderIds(currentTenantId, batchSize)) {
            try {
                transactionTemplate.executeWithoutResult(status -> lockOrderSettlementEligibility(orderId));
            } catch (Exception ex) {
                log.error("商户结算起算时间补齐失败，保留该订单等待下轮重试: orderId={}", orderId, ex);
            }
        }
        int released = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Long orderId : settlementDao.selectEligibleOrderIds(currentTenantId, now, batchSize)) {
            try {
                Integer itemReleased = transactionTemplate.execute(status -> releaseEligibleOrder(orderId, now));
                released += itemReleased == null ? 0 : itemReleased;
            } catch (Exception ex) {
                // 一笔账异常只回滚本订单，其他商户和订单仍继续释放。
                log.error("商户货款到期释放失败，保留该订单等待下轮重试: orderId={}", orderId, ex);
            }
        }
        return released;
    }

    private int releaseEligibleOrder(Long orderId, LocalDateTime now) {
        DmsShopOrder order = orderDao.selectByIdForUpdate(orderId);
        if (order == null || !Integer.valueOf(3).equals(order.getStatus())
                || afterSaleDao.selectOpenByOrderId(orderId) != null) return 0;
        int released = 0;
        for (DmsMerchantSettlement settlement : settlementDao.selectByOrderId(orderId)) {
            if (!"PENDING".equals(settlement.getStatus())) continue;
            DmsMerchantSettlement locked = settlementDao.selectByIdForUpdate(settlement.getId());
            if (locked == null || !"PENDING".equals(locked.getStatus())) continue;
            LocalDateTime eligibleTime = locked.getEligibleTime();
            if (eligibleTime == null || now.isBefore(eligibleTime)) continue;
            BigDecimal net = money(locked.getSettlementAmount()).subtract(money(locked.getReversedAmount()));
            DmsMerchant merchant = requireMerchant(locked.getMerchantId(), false);
            if (!"ENABLED".equals(merchant.getSettlementStatus())) continue;
            DmsMerchantAccount before = accountDao.selectByMerchantIdForUpdate(locked.getMerchantId());
            if (net.compareTo(ZERO) > 0 && accountDao.releasePending(locked.getMerchantId(), net) != 1) {
                Asserts.fail("商户待结算余额释放失败");
            }
            if (net.compareTo(ZERO) > 0) recordLedger(merchant, before, "SETTLEMENT_RELEASE",
                    String.valueOf(locked.getId()), "结算到期转为可提现：" + locked.getOrderNo());
            if (settlementDao.markAvailable(locked.getId()) == 1) released++;
        }
        return released;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseAfterSaleItems(List<DmsShopAfterSaleItem> items) {
        com.macro.mall.distribution.util.ShopQuantityChecks.refundLines(items);
        for (DmsShopAfterSaleItem item : items == null ? List.<DmsShopAfterSaleItem>of() : items) {
            DmsMerchantSettlement settlement = settlementDao.selectByOrderItemIdForUpdate(item.getOrderItemId());
            if (settlement == null) continue;
            int quantity = item.getRefundQuantity() == null ? 0 : item.getRefundQuantity();
            if (quantity <= 0 || quantity > settlement.getQuantity() - settlement.getRefundedQuantity()) {
                Asserts.fail("商户货款退款数量异常");
            }
            BigDecimal amount = money(settlement.getCostAmount()).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            DmsMerchantAccount before = accountDao.selectByMerchantIdForUpdate(settlement.getMerchantId());
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
            recordLedger(requireMerchant(settlement.getMerchantId(), false), before, "AFTER_SALE_REVERSAL",
                    settlement.getId() + ":" + money(settlement.getReversedAmount()).add(amount), "售后退款冲回订单货款");
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
        merchant.setLegalEntityName(trim(merchant.getLegalEntityName()));
        merchant.setUnifiedSocialCreditCode(upper(merchant.getUnifiedSocialCreditCode()));
        merchant.setBankAccountName(trim(merchant.getBankAccountName()));
        merchant.setBankName(trim(merchant.getBankName()));
        merchant.setBankAccountNo(trim(merchant.getBankAccountNo()));
        merchant.setInvoiceTitle(trim(merchant.getInvoiceTitle()));
        merchant.setTaxpayerIdentificationNo(upper(merchant.getTaxpayerIdentificationNo()));
        String contractStatus = upper(merchant.getContractStatus());
        merchant.setContractStatus(contractStatus == null ? "PENDING" : contractStatus);
        if (!CONTRACT_STATES.contains(merchant.getContractStatus())) Asserts.fail("商户合同状态不正确");
        merchant.setRequiredDepositAmount(money(merchant.getRequiredDepositAmount()));
        if (merchant.getRequiredDepositAmount().compareTo(ZERO) < 0) Asserts.fail("应缴保证金不能小于0");
        merchant.setProfileVersion(merchant.getProfileVersion() == null ? 1 : Math.max(1, merchant.getProfileVersion()));
        merchant.setSettlementMode("COST_PRICE");
        merchant.setDefaultSettlementDays(normalizeSettlementDays(merchant.getDefaultSettlementDays()));
        merchant.setAccountStatus(normalizeState(merchant.getAccountStatus(), "ENABLED", ACCOUNT_STATES, "账号状态"));
        merchant.setBusinessStatus(normalizeState(merchant.getBusinessStatus(), "ACTIVE", BUSINESS_STATES, "经营状态"));
        merchant.setFulfillmentStatus(normalizeState(merchant.getFulfillmentStatus(), "ENABLED", FULFILLMENT_STATES, "履约状态"));
        merchant.setWithdrawalStatus(normalizeState(merchant.getWithdrawalStatus(), "ENABLED", BINARY_CONTROL_STATES, "提现状态"));
        merchant.setSettlementStatus(normalizeState(merchant.getSettlementStatus(), "ENABLED", BINARY_CONTROL_STATES, "结算状态"));
        merchant.setDepositStatus(normalizeState(merchant.getDepositStatus(), "NORMAL", DEPOSIT_STATES, "保证金状态"));
        merchant.setAuditStatus(normalizeState(merchant.getAuditStatus(), "APPROVED", AUDIT_STATES, "审核状态"));
        merchant.setExitStatus(normalizeState(merchant.getExitStatus(), "NORMAL", EXIT_STATES, "退出状态"));
        merchant.setStatus(merchant.getStatus() == null ? 1 : merchant.getStatus());
        merchant.setRemark(trim(merchant.getRemark()));
    }

    private DmsMerchant requireMerchant(Long id, boolean active) {
        DmsMerchant merchant = id == null ? null : merchantDao.selectById(id);
        if (merchant == null || !tenantId().equals(merchant.getTenantId())) Asserts.fail("商户不存在");
        if (active && !Integer.valueOf(1).equals(merchant.getStatus())) Asserts.fail("商户已停用");
        return merchant;
    }

    private DmsMerchant requireMerchantForUpdate(Long id, boolean active) {
        DmsMerchant merchant = id == null ? null : merchantDao.selectByIdForUpdate(tenantId(), id);
        if (merchant == null) Asserts.fail("商户不存在");
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
    private Long currentMerchantId() {
        return AdminContext.get() == null ? null : AdminContext.get().getMerchantId();
    }
    private Long resolveMerchantScope(Long requested) {
        Long current = currentMerchantId();
        return current == null ? requested : current;
    }
    private int normalizeSettlementDays(Integer days) {
        int value = days == null ? 0 : days;
        if (value < 0 || value > 365) Asserts.fail("结算等待天数必须在0到365天之间");
        return value;
    }
    private LocalDateTime settlementEligibleTime(DmsShopOrder order, Integer delayDays) {
        if (order == null || order.getReceiveTime() == null) return null;
        LocalDateTime afterSaleDeadline = afterSaleWindowPolicy.deadline(order);
        LocalDateTime start = afterSaleDeadline == null || afterSaleDeadline.isBefore(order.getReceiveTime())
                ? order.getReceiveTime() : afterSaleDeadline;
        return start.plusDays(normalizeSettlementDays(delayDays));
    }

    private DmsMerchantDepositFlow adjustDeposit(MerchantDepositAdjustDTO dto, String operationType) {
        requirePlatformAdmin();
        if (dto == null) Asserts.fail("保证金调整不能为空");
        String operationNo = trim(dto.getOperationNo());
        if (operationNo == null) Asserts.fail("缺少操作请求号");
        DmsMerchantDepositFlow replay = depositFlowDao.selectByOperationNo(operationNo);
        if (replay != null) {
            if (!tenantId().equals(replay.getTenantId()) || !dto.getMerchantId().equals(replay.getMerchantId())
                    || !operationType.equals(replay.getOperationType())
                    || money(dto.getAmount()).compareTo(money(replay.getAmount())) != 0) {
                Asserts.fail("操作请求号已被其他保证金业务使用");
            }
            return replay;
        }
        DmsMerchant merchant = requireMerchant(dto.getMerchantId(), true);
        BigDecimal amount = money(dto.getAmount());
        if (amount.compareTo(ZERO) <= 0) Asserts.fail("保证金金额必须大于0");
        String reason = trim(dto.getReason());
        if (reason == null) Asserts.fail("请填写保证金调整原因");
        DmsMerchantAccount account = accountDao.selectByMerchantIdForUpdate(merchant.getId());
        if (account == null) Asserts.fail("商户货款账户不存在");
        int changed = switch (operationType) {
            case "FREEZE" -> accountDao.freezeDeposit(merchant.getId(), amount);
            case "RECEIVE" -> accountDao.receiveDeposit(merchant.getId(), amount);
            case "RELEASE" -> accountDao.releaseDeposit(merchant.getId(), amount);
            default -> throw new IllegalArgumentException("unsupported deposit operation");
        };
        if (changed != 1) Asserts.fail("FREEZE".equals(operationType)
                ? "商户可提现余额不足，不能冻结保证金" : "商户保证金余额不足，不能调整");
        DmsMerchantAccount after = accountDao.selectByMerchantId(merchant.getId());
        DmsAdminUser admin = AdminContext.get();
        DmsMerchantDepositFlow flow = new DmsMerchantDepositFlow();
        flow.setTenantId(merchant.getTenantId());
        flow.setMerchantId(merchant.getId());
        flow.setOperationNo(operationNo);
        flow.setOperationType(operationType);
        flow.setAmount(amount);
        flow.setBalanceAfter(money(after.getDepositFrozenAmount()));
        flow.setReason(reason);
        flow.setOperatorId(admin == null ? null : admin.getId());
        flow.setOperatorName(admin == null ? null : (trim(admin.getNickname()) == null ? admin.getUsername() : admin.getNickname().trim()));
        depositFlowDao.insert(flow);
        recordLedger(merchant, account, "DEPOSIT_" + operationType, operationNo, "商户保证金" + operationType + "：" + reason);
        return depositFlowDao.selectByOperationNo(operationNo);
    }
    private void requirePlatformAdmin() {
        if (AdminContext.get() != null && AdminContext.get().getMerchantId() != null) Asserts.fail("商户工作台账号不能维护商户资料");
    }

    private DmsAdminUser requirePlatformActorAndVerify(String currentPassword) {
        requirePlatformAdmin();
        DmsAdminUser actor = AdminContext.get();
        if (actor == null) Asserts.fail("后台登录已失效，请重新登录");
        adminAuthService.requirePermission(actor, "system:manage");
        adminAuthService.verifyPassword(actor, currentPassword);
        return actor;
    }

    private void validateMerchantProfile(DmsMerchant merchant) {
        if (trim(merchant.getLegalEntityName()) == null) Asserts.fail("请填写经营主体");
        if (merchant.getUnifiedSocialCreditCode() == null || !merchant.getUnifiedSocialCreditCode().matches("^[0-9A-HJ-NPQRTUWXY]{18}$")) {
            Asserts.fail("请填写18位统一社会信用代码");
        }
        if (trim(merchant.getBankAccountName()) == null || trim(merchant.getBankName()) == null || trim(merchant.getBankAccountNo()) == null) {
            Asserts.fail("请完整填写收款账户资料");
        }
        if (trim(merchant.getInvoiceTitle()) == null || merchant.getTaxpayerIdentificationNo() == null
                || !merchant.getTaxpayerIdentificationNo().matches("^[0-9A-HJ-NPQRTUWXY]{18}$")) {
            Asserts.fail("请填写发票抬头和18位纳税人识别号");
        }
    }

    private void requirePayoutProfile(DmsMerchant merchant) {
        if (!"SIGNED".equals(merchant.getContractStatus())) Asserts.fail("商户合同尚未生效，不能申请提现");
        if (trim(merchant.getLegalEntityName()) == null || trim(merchant.getUnifiedSocialCreditCode()) == null
                || trim(merchant.getBankAccountName()) == null || trim(merchant.getBankName()) == null
                || trim(merchant.getBankAccountNo()) == null) {
            Asserts.fail("商户经营主体或收款账户资料不完整，不能申请提现");
        }
    }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { String text = trim(value); return text == null ? null : text.toUpperCase(Locale.ROOT); }

    private void normalizeControls(MerchantControlDTO dto) {
        if (dto == null) Asserts.fail("商户控制状态不能为空");
        dto.setAccountStatus(normalizeState(dto.getAccountStatus(), null, ACCOUNT_STATES, "账号状态"));
        dto.setBusinessStatus(normalizeState(dto.getBusinessStatus(), null, BUSINESS_STATES, "经营状态"));
        dto.setFulfillmentStatus(normalizeState(dto.getFulfillmentStatus(), null, FULFILLMENT_STATES, "履约状态"));
        dto.setWithdrawalStatus(normalizeState(dto.getWithdrawalStatus(), null, BINARY_CONTROL_STATES, "提现状态"));
        dto.setSettlementStatus(normalizeState(dto.getSettlementStatus(), null, BINARY_CONTROL_STATES, "结算状态"));
        dto.setDepositStatus(normalizeState(dto.getDepositStatus(), null, DEPOSIT_STATES, "保证金状态"));
        dto.setAuditStatus(normalizeState(dto.getAuditStatus(), null, AUDIT_STATES, "审核状态"));
        dto.setExitStatus(normalizeState(dto.getExitStatus(), null, EXIT_STATES, "退出状态"));
        if (trim(dto.getReason()) == null) Asserts.fail("请填写状态调整原因");
    }

    private void validateExitTransition(DmsMerchant merchant, MerchantControlDTO dto) {
        if ("EXITING".equals(dto.getExitStatus())) {
            if ("ACTIVE".equals(dto.getBusinessStatus()) || "ENABLED".equals(dto.getWithdrawalStatus())) {
                Asserts.fail("进入清退中前必须先暂停新销售并冻结新增提现；历史订单和售后仍可继续履约");
            }
            return;
        }
        if (!"EXITED".equals(dto.getExitStatus())) return;
        if (!"DISABLED".equals(dto.getAccountStatus()) || !"CLOSED".equals(dto.getBusinessStatus())
                || !"DISABLED".equals(dto.getFulfillmentStatus()) || !"FROZEN".equals(dto.getWithdrawalStatus())
                || !"FROZEN".equals(dto.getSettlementStatus())) {
            Asserts.fail("确认已退出时必须同时禁止登录、停止经营和履约，并冻结提现及结算");
        }
        MerchantExitReadinessVO readiness = getExitReadiness(merchant.getId());
        if (!Boolean.TRUE.equals(readiness.getReady())) {
            Asserts.fail("商户尚不能退出：" + String.join("；", readiness.getBlockers()));
        }
    }

    private void addCountBlocker(List<String> blockers, Integer count, String label) {
        int value = count == null ? 0 : count;
        if (value > 0) blockers.add(label + " " + value + " 笔");
    }

    private void addMoneyBlocker(List<String> blockers, BigDecimal amount, String label) {
        BigDecimal value = money(amount);
        if (value.compareTo(ZERO) != 0) blockers.add(label + " ¥" + value.toPlainString() + " 未清零");
    }

    private String actionReason(MerchantWithdrawalActionDTO dto, String emptyMessage) {
        String reason = trim(dto == null ? null : dto.getReason());
        if (reason == null) Asserts.fail(emptyMessage);
        return reason;
    }

    private BigDecimal difference(BigDecimal accountValue, BigDecimal ledgerValue) {
        if (ledgerValue == null) return money(accountValue);
        return money(accountValue).subtract(money(ledgerValue)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean allZero(BigDecimal... values) {
        for (BigDecimal value : values) if (money(value).compareTo(ZERO) != 0) return false;
        return true;
    }

    private String normalizeState(String value, String defaults, Set<String> allowed, String label) {
        String normalized = upper(value);
        if (normalized == null) normalized = defaults;
        if (normalized == null || !allowed.contains(normalized)) Asserts.fail(label + "不正确");
        return normalized;
    }

    private MerchantControlDTO controlsOf(DmsMerchant merchant) {
        MerchantControlDTO dto = new MerchantControlDTO();
        dto.setAccountStatus(merchant.getAccountStatus()); dto.setBusinessStatus(merchant.getBusinessStatus());
        dto.setFulfillmentStatus(merchant.getFulfillmentStatus()); dto.setWithdrawalStatus(merchant.getWithdrawalStatus());
        dto.setSettlementStatus(merchant.getSettlementStatus()); dto.setDepositStatus(merchant.getDepositStatus());
        dto.setAuditStatus(merchant.getAuditStatus()); dto.setExitStatus(merchant.getExitStatus());
        return dto;
    }

    private String controlSummary(DmsMerchant merchant) { return controlSummary(controlsOf(merchant)); }
    private String controlSummary(MerchantControlDTO dto) {
        return "account=" + dto.getAccountStatus() + ",business=" + dto.getBusinessStatus()
                + ",fulfillment=" + dto.getFulfillmentStatus() + ",withdrawal=" + dto.getWithdrawalStatus()
                + ",settlement=" + dto.getSettlementStatus() + ",deposit=" + dto.getDepositStatus()
                + ",audit=" + dto.getAuditStatus() + ",exit=" + dto.getExitStatus();
    }

    private String merchantProfileSummary(DmsMerchant merchant) {
        if (merchant == null) return null;
        return "name=" + trim(merchant.getMerchantName())
                + ",legalEntity=" + MemberAccountUtils.maskPersonName(merchant.getLegalEntityName())
                + ",creditCode=" + MemberAccountUtils.maskAccount(merchant.getUnifiedSocialCreditCode())
                + ",bankAccountName=" + MemberAccountUtils.maskPersonName(merchant.getBankAccountName())
                + ",bankAccountNo=" + MemberAccountUtils.maskBankAccount(merchant.getBankAccountNo())
                + ",contract=" + merchant.getContractStatus()
                + ",requiredDeposit=" + money(merchant.getRequiredDepositAmount()).toPlainString()
                + ",settlementDays=" + merchant.getDefaultSettlementDays();
    }

    private void recordLedger(DmsMerchant merchant, DmsMerchantAccount before, String bizType, String bizId, String summary) {
        if (ledgerDao.selectByBusiness(merchant.getTenantId(), merchant.getId(), bizType, bizId) != null) {
            Asserts.fail("商户资金业务已记账，请勿重复处理");
        }
        DmsMerchantAccount after = accountDao.selectByMerchantId(merchant.getId());
        if (before == null || after == null) Asserts.fail("商户资金账户快照不存在");
        DmsMerchantLedger ledger = new DmsMerchantLedger();
        ledger.setTenantId(merchant.getTenantId()); ledger.setMerchantId(merchant.getId()); ledger.setMerchantName(merchant.getMerchantName());
        ledger.setLedgerNo("ML" + IdUtil.getSnowflakeNextId()); ledger.setBizType(bizType); ledger.setBizId(bizId); ledger.setSummary(summary);
        ledger.setPendingDelta(money(after.getPendingAmount()).subtract(money(before.getPendingAmount())));
        ledger.setAvailableDelta(money(after.getAvailableAmount()).subtract(money(before.getAvailableAmount())));
        ledger.setFrozenDelta(money(after.getFrozenAmount()).subtract(money(before.getFrozenAmount())));
        ledger.setDepositDelta(money(after.getDepositFrozenAmount()).subtract(money(before.getDepositFrozenAmount())));
        ledger.setDebtDelta(money(after.getDebtAmount()).subtract(money(before.getDebtAmount())));
        ledger.setPaidDelta(money(after.getTotalPaidAmount()).subtract(money(before.getTotalPaidAmount())));
        ledger.setPendingAfter(money(after.getPendingAmount())); ledger.setAvailableAfter(money(after.getAvailableAmount()));
        ledger.setFrozenAfter(money(after.getFrozenAmount())); ledger.setDepositAfter(money(after.getDepositFrozenAmount()));
        ledger.setDebtAfter(money(after.getDebtAmount())); ledger.setPaidAfter(money(after.getTotalPaidAmount()));
        DmsAdminUser admin = AdminContext.get();
        ledger.setOperatorId(admin == null ? null : admin.getId());
        ledger.setOperatorName(admin == null ? null : (trim(admin.getNickname()) == null ? admin.getUsername() : admin.getNickname().trim()));
        if (ledgerDao.insert(ledger) != 1) Asserts.fail("商户资金流水保存失败");
    }

    private void recordOpeningLedger(DmsMerchant merchant) {
        DmsMerchantAccount account = accountDao.selectByMerchantId(merchant.getId());
        if (account == null) Asserts.fail("商户货款账户创建失败");
        if (ledgerDao.selectByBusiness(merchant.getTenantId(), merchant.getId(), "OPENING_BALANCE", String.valueOf(merchant.getId())) != null) return;
        DmsMerchantLedger ledger = new DmsMerchantLedger();
        ledger.setTenantId(merchant.getTenantId()); ledger.setMerchantId(merchant.getId()); ledger.setMerchantName(merchant.getMerchantName());
        ledger.setLedgerNo("ML" + IdUtil.getSnowflakeNextId()); ledger.setBizType("OPENING_BALANCE");
        ledger.setBizId(String.valueOf(merchant.getId())); ledger.setSummary("商户资金账本期初余额");
        ledger.setPendingDelta(money(account.getPendingAmount())); ledger.setAvailableDelta(money(account.getAvailableAmount()));
        ledger.setFrozenDelta(money(account.getFrozenAmount())); ledger.setDepositDelta(money(account.getDepositFrozenAmount()));
        ledger.setDebtDelta(money(account.getDebtAmount())); ledger.setPaidDelta(money(account.getTotalPaidAmount()));
        ledger.setPendingAfter(money(account.getPendingAmount())); ledger.setAvailableAfter(money(account.getAvailableAmount()));
        ledger.setFrozenAfter(money(account.getFrozenAmount())); ledger.setDepositAfter(money(account.getDepositFrozenAmount()));
        ledger.setDebtAfter(money(account.getDebtAmount())); ledger.setPaidAfter(money(account.getTotalPaidAmount()));
        if (ledgerDao.insert(ledger) != 1) Asserts.fail("商户资金期初流水保存失败");
    }

    private void recordWithdrawalEvent(DmsMerchantWithdrawal withdrawal, String from, String to, String remark) {
        DmsAdminUser admin = AdminContext.get();
        DmsMerchantWithdrawalEvent event = new DmsMerchantWithdrawalEvent();
        event.setTenantId(withdrawal.getTenantId()); event.setMerchantId(withdrawal.getMerchantId());
        event.setWithdrawalId(withdrawal.getId()); event.setWithdrawalNo(withdrawal.getWithdrawalNo());
        event.setFromStatus(from); event.setToStatus(to); event.setRemark(remark);
        event.setOperatorId(admin == null ? null : admin.getId());
        event.setOperatorName(admin == null ? null : (trim(admin.getNickname()) == null ? admin.getUsername() : admin.getNickname().trim()));
        if (withdrawalEventDao.insert(event) != 1) Asserts.fail("提现审批轨迹保存失败");
    }
}
