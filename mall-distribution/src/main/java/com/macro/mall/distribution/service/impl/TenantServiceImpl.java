package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantConfigVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantConfigVersion;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.BrandCultureImagePolicy;
import com.macro.mall.distribution.service.ShopCatalogCacheService;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.service.TenantLegalTemplateSupport;
import com.macro.mall.distribution.vo.TenantConfigVersionVO;
import com.macro.mall.distribution.vo.TenantLegalTemplatesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes.DISABLED;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final DateTimeFormatter VERSION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final DmsTenantDao tenantDao;
    private final DmsCommissionRuleVersionDao versionDao;
    private final DmsTenantDisplayConfigDao displayConfigDao;
    private final DmsTenantConfigVersionDao configVersionDao;
    private final TenantDisplayConfigSupport displayConfigSupport;
    private final TenantLegalTemplateSupport legalTemplateSupport;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ShopCatalogCacheService catalogCache;
    private final AdminAuthService adminAuthService;
    private final BrandCultureImagePolicy brandCultureImagePolicy;

    @Override
    public List<DmsTenant> listTenants() {
        List<DmsTenant> tenants = tenantDao.selectAll();
        tenants.forEach(legalTemplateSupport::applyDefaults);
        return tenants;
    }

    @Override
    public DmsTenant getTenant(Long id) {
        DmsTenant tenant = tenantDao.selectById(id == null ? 1L : id);
        legalTemplateSupport.applyDefaults(tenant);
        return tenant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsTenant saveTenant(DmsTenant tenant) {
        if (tenant == null) {
            Asserts.fail("商城资料不能为空");
        }
        DmsTenant before = tenant.getId() == null ? null : tenantDao.selectById(tenant.getId());
        if (tenant.getTenantName() == null || tenant.getTenantName().isBlank()) {
            Asserts.fail("公司名称不能为空");
        }
        if (tenant.getTenantCode() == null || tenant.getTenantCode().isBlank()) {
            tenant.setTenantCode("T" + IdUtil.getSnowflakeNextIdStr());
        }
        if (tenant.getBrandName() == null || tenant.getBrandName().isBlank()) {
            tenant.setBrandName(tenant.getTenantName());
        }
        if (tenant.getThemeColor() == null || tenant.getThemeColor().isBlank()) {
            tenant.setThemeColor("#e7193f");
        } else if (!tenant.getThemeColor().trim().matches("^#[0-9a-fA-F]{6}$")) {
            Asserts.fail("主题色格式不正确，请使用6位十六进制色值");
        } else {
            tenant.setThemeColor(tenant.getThemeColor().trim().toLowerCase());
        }
        if (tenant.getProductTemplate() == null || tenant.getProductTemplate().isBlank()) {
            tenant.setProductTemplate("retail-red");
        } else if (!List.of("retail-red", "fresh-green", "premium-gold", "soft-purple",
                "standard", "beauty", "food", "course", "health").contains(tenant.getProductTemplate())) {
            Asserts.fail("不支持的前台样式");
        }
        tenant.setBrandCultureEnabled(Integer.valueOf(1).equals(tenant.getBrandCultureEnabled()) ? 1 : 0);
        tenant.setBrandCultureCoverUrl(brandCultureImagePolicy.validateCover(
                tenant.getId(), tenant.getBrandCultureCoverUrl(), before == null ? null : before.getBrandCultureCoverUrl()));
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        if (tenant.getAfterSaleWindowMode() == null || tenant.getAfterSaleWindowMode().isBlank()) {
            tenant.setAfterSaleWindowMode(ShopAfterSaleWindowPolicy.MODE_RECEIVED);
        } else {
            tenant.setAfterSaleWindowMode(tenant.getAfterSaleWindowMode().trim().toUpperCase());
            if (!List.of(ShopAfterSaleWindowPolicy.MODE_RECEIVED, ShopAfterSaleWindowPolicy.MODE_ORDER_CREATED)
                    .contains(tenant.getAfterSaleWindowMode())) {
                Asserts.fail("售后期限起算方式不正确");
            }
        }
        if (tenant.getAfterSaleWindowDays() == null) {
            tenant.setAfterSaleWindowDays(7);
        } else if (tenant.getAfterSaleWindowDays() < 0 || tenant.getAfterSaleWindowDays() > 365) {
            Asserts.fail("售后申请期限应设置为0至365天");
        }
        normalizeBusinessModes(tenant);
        requireBusinessModeAuthority(tenant);
        if (tenant.getPoliceRecordUrl() != null && !tenant.getPoliceRecordUrl().isBlank()) {
            String policeRecordUrl = tenant.getPoliceRecordUrl().trim();
            if (!policeRecordUrl.matches("^https://.+")) {
                Asserts.fail("公安备案链接必须使用 https://");
            }
            tenant.setPoliceRecordUrl(policeRecordUrl);
        }
        if (tenant.getUnifiedSocialCreditCode() != null && !tenant.getUnifiedSocialCreditCode().isBlank()) {
            String creditCode = tenant.getUnifiedSocialCreditCode().trim().toUpperCase();
            if (!creditCode.matches("^[0-9A-HJ-NPQRTUWXY]{18}$")) {
                Asserts.fail("统一社会信用代码格式不正确，请填写18位代码");
            }
            tenant.setUnifiedSocialCreditCode(creditCode);
        }
        legalTemplateSupport.applyDefaults(tenant);

        if (tenant.getId() == null) {
            tenantDao.insert(tenant);
            createDefaultVersion(tenant);
            displayConfigDao.insert(defaultDisplayConfig(tenant.getId()));
            recordConfigVersion(tenant.getId(), "INITIAL", null);
        } else {
            ensureBaselineVersion(tenant.getId());
            tenantDao.update(tenant);
            recordConfigVersion(tenant.getId(), "PROFILE_UPDATE", null);
        }
        catalogCache.invalidateAfterCommit(tenant.getId());
        DmsTenant saved = getTenant(tenant.getId());
        operationLogService.log("TENANT_CONFIG", before == null ? "CREATE" : "PROFILE_UPDATE", "TENANT",
                String.valueOf(tenant.getId()), tenantSummary(before), tenantSummary(saved),
                before == null ? "创建商城客户配置" : "更新商城品牌、经营资料或业务模式");
        return saved;
    }

    private void normalizeBusinessModes(DmsTenant tenant) {
        tenant.setFlashSaleEnabled(Integer.valueOf(1).equals(tenant.getFlashSaleEnabled()) ? 1 : 0);
        tenant.setRepurchaseMallEnabled(Integer.valueOf(1).equals(tenant.getRepurchaseMallEnabled()) ? 1 : 0);
        tenant.setFlashSaleBonusMode(normalizeMode(tenant.getFlashSaleBonusMode(),
                List.of("NONE", "STANDARD", "CUSTOM"), "NONE", "秒杀奖金模式"));
        tenant.setRepurchaseBonusMode(normalizeMode(tenant.getRepurchaseBonusMode(),
                List.of("NONE", "STANDARD", "CUSTOM"), "NONE", "复购奖金模式"));
        tenant.setRepurchaseEligibilityMode(normalizeMode(tenant.getRepurchaseEligibilityMode(),
                List.of("PAID_MEMBER", "AGENT", "ALL_MEMBER"), "PAID_MEMBER", "复购准入模式"));
    }

    private void requireBusinessModeAuthority(DmsTenant requested) {
        DmsAdminUser admin = AdminContext.get();
        if (admin == null) return;
        DmsTenant existing = requested.getId() == null ? null : tenantDao.selectById(requested.getId());
        boolean changed = existing == null
                ? Integer.valueOf(1).equals(requested.getFlashSaleEnabled())
                    || Integer.valueOf(1).equals(requested.getRepurchaseMallEnabled())
                    || !"NONE".equals(requested.getFlashSaleBonusMode())
                    || !"NONE".equals(requested.getRepurchaseBonusMode())
                : !java.util.Objects.equals(normalizedFlag(existing.getFlashSaleEnabled()), requested.getFlashSaleEnabled())
                    || !java.util.Objects.equals(normalizedFlag(existing.getRepurchaseMallEnabled()), requested.getRepurchaseMallEnabled())
                    || !java.util.Objects.equals(normalizeMode(existing.getFlashSaleBonusMode(),
                        List.of("NONE", "STANDARD", "CUSTOM"), "NONE", "秒杀奖金模式"), requested.getFlashSaleBonusMode())
                    || !java.util.Objects.equals(normalizeMode(existing.getRepurchaseBonusMode(),
                        List.of("NONE", "STANDARD", "CUSTOM"), "NONE", "复购奖金模式"), requested.getRepurchaseBonusMode())
                    || !java.util.Objects.equals(normalizeMode(existing.getRepurchaseEligibilityMode(),
                        List.of("PAID_MEMBER", "AGENT", "ALL_MEMBER"), "PAID_MEMBER", "复购准入模式"),
                        requested.getRepurchaseEligibilityMode());
        if (changed) adminAuthService.requirePermission(admin, "config:bonus");
    }

    private Integer normalizedFlag(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private String tenantSummary(DmsTenant tenant) {
        if (tenant == null) return null;
        return "name=" + tenant.getTenantName() + ";brand=" + tenant.getBrandName()
                + ";status=" + tenant.getStatus() + ";flashSale=" + tenant.getFlashSaleEnabled()
                + ";repurchase=" + tenant.getRepurchaseMallEnabled()
                + ";flashBonusMode=" + tenant.getFlashSaleBonusMode()
                + ";repurchaseBonusMode=" + tenant.getRepurchaseBonusMode()
                + ";afterSaleMode=" + tenant.getAfterSaleWindowMode()
                + ";afterSaleDays=" + tenant.getAfterSaleWindowDays();
    }

    private String displaySummary(DmsTenantDisplayConfig config) {
        if (config == null) return null;
        return "showPv=" + config.getShowPv() + ";showTeamPerformance=" + config.getShowTeamPerformance()
                + ";showBonusSource=" + config.getShowBonusSource()
                + ";showBonusFlow=" + config.getShowBonusFlow()
                + ";layout=" + config.getLayoutTemplate();
    }

    private String normalizeMode(String value, List<String> allowed, String defaultValue, String label) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) Asserts.fail(label + "不正确");
        return normalized;
    }

    @Override
    public TenantLegalTemplatesVO getLegalTemplates() {
        return legalTemplateSupport.templates();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTenantStatus(Long id, Integer status) {
        DmsTenant before = id == null ? null : tenantDao.selectById(id);
        if (before == null) {
            Asserts.fail("商城客户不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            Asserts.fail("商城状态不正确");
        }
        ensureBaselineVersion(id);
        boolean updated = tenantDao.updateStatus(id, status) > 0;
        if (updated) {
            recordConfigVersion(id, "STATUS_UPDATE", null);
            catalogCache.invalidateAfterCommit(id);
            operationLogService.log("TENANT_CONFIG", "STATUS_UPDATE", "TENANT", String.valueOf(id),
                    "status=" + before.getStatus(), "status=" + status, "更新商城客户启用状态");
        }
        return updated;
    }

    @Override
    public List<DmsCommissionRuleVersion> listRuleVersions(Long tenantId) {
        return versionDao.selectByTenantId(tenantId);
    }

    @Override
    public DmsTenantDisplayConfig getDisplayConfig(Long tenantId) {
        Long resolvedTenantId = tenantId == null ? 1L : tenantId;
        DmsTenantDisplayConfig config = displayConfigDao.selectByTenantId(resolvedTenantId);
        return displayConfigSupport.prepareForRead(config, resolvedTenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsTenantDisplayConfig saveDisplayConfig(DmsTenantDisplayConfig config) {
        if (config == null || config.getTenantId() == null) {
            Asserts.fail("租户ID不能为空");
        }
        if (config.getNewArrivalWindowDays() != null
                && config.getNewArrivalWindowDays() != 0
                && (config.getNewArrivalWindowDays() < 30 || config.getNewArrivalWindowDays() > 365)) {
            Asserts.fail("自动新品展示时间只能设置为30至365天，或选择永久");
        }
        displayConfigSupport.hydrateBrandCultureImages(config);
        config.setBrandCultureDetailImages(brandCultureImagePolicy.validate(
                config.getTenantId(), config.getBrandCultureDetailImages()));
        displayConfigSupport.prepareForSave(config);
        DmsTenantDisplayConfig exists = displayConfigDao.selectByTenantId(config.getTenantId());
        ensureBaselineVersion(config.getTenantId());
        if (exists == null) {
            displayConfigDao.insert(config);
        } else {
            displayConfigDao.update(config);
        }
        recordConfigVersion(config.getTenantId(), "DISPLAY_UPDATE", null);
        catalogCache.invalidateAfterCommit(config.getTenantId());
        DmsTenantDisplayConfig saved = displayConfigSupport.prepareForRead(
                displayConfigDao.selectByTenantId(config.getTenantId()), config.getTenantId());
        operationLogService.log("TENANT_CONFIG", "DISPLAY_UPDATE", "TENANT",
                String.valueOf(config.getTenantId()), displaySummary(exists), displaySummary(saved),
                "更新商城页面与会员端展示配置");
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TenantConfigVersionVO> listConfigVersions(Long tenantId) {
        Long resolvedTenantId = tenantId == null ? 1L : tenantId;
        if (tenantDao.selectById(resolvedTenantId) == null) {
            Asserts.fail("商城客户不存在");
        }
        ensureBaselineVersion(resolvedTenantId);
        return configVersionDao.selectMetadataByTenantId(resolvedTenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsTenant restoreConfigVersion(Long tenantId, Long versionId) {
        if (tenantId == null || versionId == null) {
            Asserts.fail("请选择需要恢复的配置版本");
        }
        if (tenantDao.selectById(tenantId) == null) {
            Asserts.fail("商城客户不存在");
        }
        DmsTenantConfigVersion target = configVersionDao.selectByIdAndTenantId(versionId, tenantId);
        if (target == null) {
            Asserts.fail("配置版本不存在或不属于当前客户");
        }
        ensureBaselineVersion(tenantId);
        recordConfigVersion(tenantId, "PRE_RESTORE", null);

        DmsTenant restoredTenant = readSnapshot(target.getTenantSnapshot(), DmsTenant.class, "商城资料");
        DmsTenantDisplayConfig restoredDisplay = readSnapshot(
                target.getDisplaySnapshot(), DmsTenantDisplayConfig.class, "商城视觉配置");
        restoredTenant.setId(tenantId);
        if (restoredTenant.getAfterSaleWindowMode() == null || restoredTenant.getAfterSaleWindowMode().isBlank()) {
            restoredTenant.setAfterSaleWindowMode(ShopAfterSaleWindowPolicy.MODE_RECEIVED);
        }
        if (restoredTenant.getAfterSaleWindowDays() == null) {
            restoredTenant.setAfterSaleWindowDays(7);
        }
        normalizeBusinessModes(restoredTenant);
        legalTemplateSupport.applyDefaults(restoredTenant);
        if (tenantDao.update(restoredTenant) == 0) {
            Asserts.fail("恢复商城资料失败");
        }

        restoredDisplay.setTenantId(tenantId);
        displayConfigSupport.hydrateBrandCultureImages(restoredDisplay);
        restoredDisplay.setBrandCultureDetailImages(brandCultureImagePolicy.validate(
                tenantId, restoredDisplay.getBrandCultureDetailImages()));
        displayConfigSupport.prepareForSave(restoredDisplay);
        if (displayConfigDao.selectByTenantId(tenantId) == null) {
            displayConfigDao.insert(restoredDisplay);
        } else {
            displayConfigDao.update(restoredDisplay);
        }
        DmsTenantConfigVersion restoredVersion = recordConfigVersion(tenantId, "RESTORE", versionId);
        operationLogService.log("TENANT_CONFIG", "RESTORE", "商城配置", String.valueOf(tenantId),
                "恢复前版本", target.getVersionNo(),
                "已恢复配置版本 " + target.getVersionNo() + "，并生成新版本 " + restoredVersion.getVersionNo());
        catalogCache.invalidateAfterCommit(tenantId);
        return getTenant(tenantId);
    }

    private void createDefaultVersion(DmsTenant tenant) {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(tenant.getId());
        version.setVersionNo(DISABLED);
        version.setVersionName("客户奖金程序未接入");
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now());
        version.setRemark("商城基座安全默认值：正常交易不产生奖金，客户制度开发并验收后再替换");
        versionDao.insert(version);
    }

    private DmsTenantDisplayConfig defaultDisplayConfig(Long tenantId) {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(tenantId);
        // 新客户尚未接入独立奖金制度时，PV 等内部经营数据默认不展示。
        // 历史客户缺字段时仍由 TenantDisplayConfigSupport 的兼容默认值处理，不受这里影响。
        config.setShowPv(0);
        displayConfigSupport.prepareForSave(config);
        return config;
    }

    private void ensureBaselineVersion(Long tenantId) {
        if (configVersionDao.countByTenantId(tenantId) == 0) {
            recordConfigVersion(tenantId, "BASELINE", null);
        }
    }

    private DmsTenantConfigVersion recordConfigVersion(Long tenantId, String changeType, Long sourceVersionId) {
        DmsTenant tenant = tenantDao.selectById(tenantId);
        if (tenant == null) {
            Asserts.fail("商城客户不存在，无法生成配置版本");
        }
        legalTemplateSupport.applyDefaults(tenant);
        DmsTenantDisplayConfig display = displayConfigSupport.prepareForRead(
                displayConfigDao.selectByTenantId(tenantId), tenantId);
        DmsAdminUser admin = AdminContext.get();

        DmsTenantConfigVersion version = new DmsTenantConfigVersion();
        version.setTenantId(tenantId);
        version.setVersionNo("V" + LocalDateTime.now().format(VERSION_TIME_FORMAT)
                + "-" + IdUtil.fastSimpleUUID().substring(0, 6));
        version.setChangeType(changeType);
        version.setTenantSnapshot(writeSnapshot(tenant));
        version.setDisplaySnapshot(writeSnapshot(display));
        version.setOperatorId(admin == null ? 0L : admin.getId());
        version.setOperatorName(admin == null ? "system" : admin.getUsername());
        version.setSourceVersionId(sourceVersionId);
        configVersionDao.insert(version);
        return version;
    }

    private String writeSnapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("生成商城配置快照失败", e);
        }
    }

    private <T> T readSnapshot(String value, Class<T> type, String label) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException(label + "历史快照无法读取", e);
        }
    }
}
