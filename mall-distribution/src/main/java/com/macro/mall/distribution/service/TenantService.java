package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.vo.TenantLegalTemplatesVO;
import com.macro.mall.distribution.vo.TenantConfigVersionVO;

import java.util.List;

public interface TenantService {

    List<DmsTenant> listTenants();

    DmsTenant getTenant(Long id);

    DmsTenant saveTenant(DmsTenant tenant);

    TenantLegalTemplatesVO getLegalTemplates();

    boolean updateTenantStatus(Long id, Integer status);

    List<DmsCommissionRuleVersion> listRuleVersions(Long tenantId);

    DmsTenantDisplayConfig getDisplayConfig(Long tenantId);

    DmsTenantDisplayConfig saveDisplayConfig(DmsTenantDisplayConfig config);

    List<TenantConfigVersionVO> listConfigVersions(Long tenantId);

    DmsTenant restoreConfigVersion(Long tenantId, Long versionId);
}
