package com.macro.mall.distribution.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.macro.mall.distribution.service.CustomerBusinessModePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.bonus.DirectReferralRuleConfig;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.DirectReferralConfigDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.DirectReferralConfigService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.vo.DirectReferralConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DirectReferralConfigServiceImpl implements DirectReferralConfigService {
    private final DmsTenantDao tenantDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    public DirectReferralConfigVO current() {
        requireAdmin(false);
        return view(ruleVersionDao.selectActiveByTenantId(TenantContext.getTenantId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DirectReferralConfigVO save(DirectReferralConfigDTO dto) {
        requireAdmin(true);
        if (dto == null) Asserts.fail("请填写直接推荐佣金配置");
        DirectReferralRuleConfig config = new DirectReferralRuleConfig(dto.getEnabled(), dto.getCommissionRate(),
                dto.getPurchaseScope(), dto.getSettlementDelayDays()).validated();
        Long tenantId = TenantContext.getTenantId();
        var tenant = tenantDao.selectByIdForUpdate(tenantId);
        if (tenant == null) Asserts.fail("商城客户不存在");
        if (Boolean.TRUE.equals(dto.getEnabled())) CustomerBusinessModePolicy.requireLegacyQualification(tenant);
        DmsCommissionRuleVersion before = ruleVersionDao.selectActiveByTenantIdForUpdate(tenantId);
        if (!Objects.equals(before == null ? null : before.getId(), dto.getExpectedVersionId())) {
            Asserts.fail("佣金规则已被更新，请刷新后重新确认");
        }
        if (isOtherPolicy(before) && !Boolean.TRUE.equals(dto.getConfirmPolicySwitch())) {
            Asserts.fail("当前使用客户自定义或历史奖金制度，请明确确认切换后再保存");
        }
        String policyCode = Boolean.TRUE.equals(config.enabled())
                ? CustomerBonusPolicyCodes.DIRECT_REFERRAL : CustomerBonusPolicyCodes.DISABLED;
        String snapshot = encode(config);
        if (before != null && policyCode.equals(before.getVersionNo())
                && snapshot.equals(before.getDirectReferralConfig())) return view(before);

        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(tenantId);
        version.setVersionNo(policyCode);
        version.setVersionName(Boolean.TRUE.equals(config.enabled()) ? "直接推荐成交佣金" : "佣金关闭");
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now());
        version.setDirectReferralConfig(snapshot);
        version.setRemark("配置独立于邀请与推广资格；历史订单按支付时规则执行");
        ruleVersionDao.deactivateActive(tenantId);
        if (ruleVersionDao.insert(version) != 1) Asserts.fail("佣金规则保存失败");
        operationLogService.log("BONUS_CONFIG", "DIRECT_REFERRAL_VERSION", "TENANT", String.valueOf(tenantId),
                before == null ? null : before.getVersionNo() + ":" + before.getDirectReferralConfig(),
                policyCode + ":" + snapshot, "创建新的佣金规则版本，不改变历史订单");
        return view(version);
    }

    @Override
    public DirectReferralRuleConfig frozen(Long tenantId, Long ruleVersionId) {
        if (tenantId == null || ruleVersionId == null) Asserts.fail("缺少支付时佣金规则版本");
        DmsCommissionRuleVersion version = ruleVersionDao.selectByIdForUpdate(tenantId, ruleVersionId);
        if (version == null || !tenantId.equals(version.getTenantId())) Asserts.fail("支付时佣金规则版本不存在");
        if (CustomerBonusPolicyCodes.DISABLED.equals(version.getVersionNo())) return DirectReferralRuleConfig.disabled();
        if (!CustomerBonusPolicyCodes.DIRECT_REFERRAL.equals(version.getVersionNo())) {
            Asserts.fail("支付时规则不是直接推荐佣金");
        }
        DirectReferralRuleConfig config = decode(version.getDirectReferralConfig());
        if (!Boolean.TRUE.equals(config.enabled())) Asserts.fail("支付时直接推荐佣金配置不完整");
        return config;
    }

    private void requireAdmin(boolean write) {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null || admin.getMerchantId() != null) {
            throw new ApiException(ResultCode.FORBIDDEN, "仅平台管理员可配置直接推荐佣金");
        }
        adminAuthService.requirePermission(admin, "config:bonus");
        if (write) adminAuthService.requirePermission(admin, "finance:manage");
    }

    private boolean isOtherPolicy(DmsCommissionRuleVersion version) {
        return version != null && !CustomerBonusPolicyCodes.DIRECT_REFERRAL.equals(version.getVersionNo())
                && !CustomerBonusPolicyCodes.DISABLED.equals(version.getVersionNo());
    }

    private DirectReferralConfigVO view(DmsCommissionRuleVersion version) {
        boolean readOnly = isOtherPolicy(version);
        DirectReferralRuleConfig config = DirectReferralRuleConfig.disabled();
        if (version != null && !readOnly && version.getDirectReferralConfig() != null) {
            config = decode(version.getDirectReferralConfig());
        } else if (version != null && CustomerBonusPolicyCodes.DIRECT_REFERRAL.equals(version.getVersionNo())) {
            Asserts.fail("直接推荐佣金版本缺少配置快照");
        }
        if (version != null && !readOnly
                && Boolean.TRUE.equals(config.enabled())
                != CustomerBonusPolicyCodes.DIRECT_REFERRAL.equals(version.getVersionNo())) {
            Asserts.fail("佣金规则编码与配置快照不一致");
        }
        DirectReferralConfigVO result = new DirectReferralConfigVO();
        result.setEnabled(config.enabled());
        result.setCommissionRate(config.commissionRate());
        result.setPurchaseScope(config.purchaseScope());
        result.setSettlementDelayDays(config.settlementDelayDays());
        result.setVersionId(version == null ? null : version.getId());
        result.setCurrentPolicyCode(version == null ? CustomerBonusPolicyCodes.DISABLED : version.getVersionNo());
        result.setVersionName(version == null ? "佣金关闭" : version.getVersionName());
        result.setEffectiveTime(version == null ? null : version.getEffectiveTime());
        result.setReadOnly(readOnly);
        return result;
    }

    private String encode(DirectReferralRuleConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new ApiException("佣金规则快照保存失败");
        }
    }

    private DirectReferralRuleConfig decode(String value) {
        if (value == null || value.isBlank()) Asserts.fail("佣金规则快照缺失");
        try {
            DirectReferralRuleConfig config = objectMapper.readValue(value, DirectReferralRuleConfig.class);
            if (config == null) Asserts.fail("佣金规则快照缺失");
            return config.validated();
        } catch (JsonProcessingException e) {
            throw new ApiException("佣金规则快照格式不正确");
        }
    }
}
