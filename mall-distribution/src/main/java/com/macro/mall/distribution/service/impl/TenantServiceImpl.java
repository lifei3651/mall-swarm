package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.service.TenantLegalTemplateSupport;
import com.macro.mall.distribution.vo.TenantLegalTemplatesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.macro.mall.distribution.service.impl.NewRetailBonusPolicy.VERSION_NO;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final DmsTenantDao tenantDao;
    private final DmsCommissionRuleVersionDao versionDao;
    private final DmsTenantDisplayConfigDao displayConfigDao;
    private final TenantDisplayConfigSupport displayConfigSupport;
    private final TenantLegalTemplateSupport legalTemplateSupport;

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
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
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
            saveDisplayConfig(defaultDisplayConfig(tenant.getId()));
        } else {
            tenantDao.update(tenant);
        }
        return getTenant(tenant.getId());
    }

    @Override
    public TenantLegalTemplatesVO getLegalTemplates() {
        return legalTemplateSupport.templates();
    }

    @Override
    public boolean updateTenantStatus(Long id, Integer status) {
        return tenantDao.updateStatus(id, status) > 0;
    }

    @Override
    public List<DmsCommissionRuleVersion> listRuleVersions(Long tenantId) {
        return versionDao.selectByTenantId(tenantId).stream()
                .filter(version -> VERSION_NO.equals(version.getVersionNo()))
                .toList();
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
        displayConfigSupport.prepareForSave(config);
        DmsTenantDisplayConfig exists = displayConfigDao.selectByTenantId(config.getTenantId());
        if (exists == null) {
            displayConfigDao.insert(config);
        } else {
            displayConfigDao.update(config);
        }
        return displayConfigSupport.prepareForRead(displayConfigDao.selectByTenantId(config.getTenantId()), config.getTenantId());
    }

    private void createDefaultVersion(DmsTenant tenant) {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setTenantId(tenant.getId());
        version.setVersionNo(VERSION_NO);
        version.setVersionName("新零售正式奖金方案");
        version.setStatus(1);
        version.setEffectiveTime(LocalDateTime.now());
        version.setRemark("唯一固定方案：八级晋升、直推奖、董事团队分红（同等级仅最近一人）");
        versionDao.insert(version);
    }

    private DmsTenantDisplayConfig defaultDisplayConfig(Long tenantId) {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(tenantId);
        displayConfigSupport.prepareForSave(config);
        return config;
    }
}
