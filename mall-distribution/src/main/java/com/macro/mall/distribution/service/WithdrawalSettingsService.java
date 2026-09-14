package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsDistributionSettingDao;
import com.macro.mall.distribution.dto.WithdrawalSettingsUpdateDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsDistributionSetting;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.vo.WithdrawalSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalSettingsService {
    private static final String KEY_PREFIX = "MEMBER_WITHDRAWAL_SETTINGS:";

    private final DmsDistributionSettingDao settingDao;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;
    private final AdminAuthService adminAuthService;

    public WithdrawalSettingsVO current() {
        DmsDistributionSetting stored = settingDao.selectByKey(key());
        if (stored == null || stored.getSettingValue() == null || stored.getSettingValue().isBlank()) return defaults();
        try {
            WithdrawalSettingsVO value = objectMapper.readValue(stored.getSettingValue(), WithdrawalSettingsVO.class);
            normalize(value);
            return value;
        } catch (Exception ignored) {
            return defaults();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawalSettingsVO update(WithdrawalSettingsUpdateDTO dto) {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null || admin.getMerchantId() != null) Asserts.fail("仅平台财务管理员可修改提现规则");
        adminAuthService.requirePermission(admin, "finance:manage");
        if (!Boolean.TRUE.equals(dto.getServiceEnabled()) && !hasText(dto.getDisabledReason())) {
            Asserts.fail("关闭提现服务时必须填写会员可见的原因");
        }
        if (Boolean.TRUE.equals(dto.getBankCardEnabled()) && !Boolean.TRUE.equals(dto.getOfflinePayoutEnabled())) {
            Asserts.fail("银行卡提现首期采用线下转账，需同时开启财务线下打款");
        }
        DmsDistributionSetting stored = settingDao.selectByKey(key());
        WithdrawalSettingsVO before = current();
        if (!before.getVersion().equals(dto.getVersion())) Asserts.fail("提现规则已被其他管理员修改，请刷新后重试");
        WithdrawalSettingsVO after = new WithdrawalSettingsVO();
        after.setServiceEnabled(dto.getServiceEnabled());
        after.setDisabledReason(Boolean.TRUE.equals(dto.getServiceEnabled()) ? "" : dto.getDisabledReason().trim());
        after.setBalanceHolderEnabled(dto.getBalanceHolderEnabled());
        after.setManualBalanceWithdrawable(dto.getManualBalanceWithdrawable());
        after.setBankCardEnabled(dto.getBankCardEnabled());
        after.setOfflinePayoutEnabled(dto.getOfflinePayoutEnabled());
        after.setVersion(before.getVersion() + 1);
        try {
            String json = objectMapper.writeValueAsString(after);
            if (stored == null) {
                DmsDistributionSetting created = new DmsDistributionSetting();
                created.setSettingKey(key());
                created.setSettingValue(json);
                created.setRemark("会员余额与提现规则");
                settingDao.insert(created);
            } else if (settingDao.updateByKeyIfValue(key(), stored.getSettingValue(), json, "会员余额与提现规则") != 1) {
                Asserts.fail("提现规则已被其他管理员修改，请刷新后重试");
            }
            operationLogService.log("WITHDRAW", "CONFIG", "WITHDRAWAL_SETTINGS", key(),
                    objectMapper.writeValueAsString(before), json, dto.getChangeReason().trim());
            return after;
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            Asserts.fail("提现规则保存失败");
            return after;
        }
    }

    public boolean serviceEnabled() { return Boolean.TRUE.equals(current().getServiceEnabled()); }
    public boolean balanceHolderEnabled() { return Boolean.TRUE.equals(current().getBalanceHolderEnabled()); }
    public boolean manualBalanceWithdrawable() { return Boolean.TRUE.equals(current().getManualBalanceWithdrawable()); }
    public boolean bankCardEnabled() { return Boolean.TRUE.equals(current().getBankCardEnabled()); }
    public boolean offlinePayoutEnabled() { return Boolean.TRUE.equals(current().getOfflinePayoutEnabled()); }

    private String key() { return KEY_PREFIX + (TenantContext.getTenantId() == null ? 1L : TenantContext.getTenantId()); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private void normalize(WithdrawalSettingsVO value) {
        if (value.getServiceEnabled() == null) value.setServiceEnabled(true);
        if (value.getDisabledReason() == null) value.setDisabledReason("");
        if (value.getBalanceHolderEnabled() == null) value.setBalanceHolderEnabled(false);
        if (value.getManualBalanceWithdrawable() == null) value.setManualBalanceWithdrawable(false);
        if (value.getBankCardEnabled() == null) value.setBankCardEnabled(false);
        if (value.getOfflinePayoutEnabled() == null) value.setOfflinePayoutEnabled(false);
        if (value.getVersion() == null || value.getVersion() < 0) value.setVersion(0L);
    }
    private WithdrawalSettingsVO defaults() { WithdrawalSettingsVO value = new WithdrawalSettingsVO(); normalize(value); return value; }
}
