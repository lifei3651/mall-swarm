package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantConfigVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import com.macro.mall.distribution.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantSafeDefaultsTest {

    @AfterEach
    void clearContext() {
        AdminContext.clear();
    }

    @Test
    void newTenantStartsWithDisabledBonusAndHiddenPv() {
        DmsTenantDao tenantDao = mock(DmsTenantDao.class);
        DmsCommissionRuleVersionDao versionDao = mock(DmsCommissionRuleVersionDao.class);
        DmsTenantDisplayConfigDao displayDao = mock(DmsTenantDisplayConfigDao.class);
        DmsTenantConfigVersionDao configVersionDao = mock(DmsTenantConfigVersionDao.class);
        TenantLegalTemplateSupport legalSupport = mock(TenantLegalTemplateSupport.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ShopCatalogCacheService catalogCache = mock(ShopCatalogCacheService.class);
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        BrandCultureImagePolicy imagePolicy = mock(BrandCultureImagePolicy.class);
        TenantDisplayConfigSupport displaySupport = new TenantDisplayConfigSupport(new ObjectMapper());

        DmsTenant tenant = new DmsTenant();
        tenant.setTenantName("新客户公司");
        doAnswer(invocation -> {
            DmsTenant inserted = invocation.getArgument(0);
            inserted.setId(7L);
            return 1;
        }).when(tenantDao).insert(any(DmsTenant.class));
        when(tenantDao.selectById(7L)).thenReturn(tenant);
        when(displayDao.selectByTenantId(7L)).thenReturn(null);
        when(configVersionDao.countByTenantId(7L)).thenReturn(0);
        when(imagePolicy.validateCover(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        TenantService service = new TenantServiceImpl(
                tenantDao, versionDao, displayDao, configVersionDao, displaySupport, legalSupport,
                operationLogService, new ObjectMapper(), catalogCache, adminAuthService, imagePolicy);
        service.saveTenant(tenant);

        ArgumentCaptor<DmsCommissionRuleVersion> version = ArgumentCaptor.forClass(DmsCommissionRuleVersion.class);
        verify(versionDao).insert(version.capture());
        assertEquals(CustomerBonusPolicyCodes.DISABLED, version.getValue().getVersionNo());
        assertEquals(1, version.getValue().getStatus());

        ArgumentCaptor<DmsTenantDisplayConfig> display = ArgumentCaptor.forClass(DmsTenantDisplayConfig.class);
        verify(displayDao).insert(display.capture());
        assertEquals(0, display.getValue().getShowPv());
        assertEquals(0, display.getValue().getShowTeamPerformance());
        assertEquals(0, display.getValue().getShowBonusSource());
        assertEquals(0, display.getValue().getShowBonusFlow());
    }
}
