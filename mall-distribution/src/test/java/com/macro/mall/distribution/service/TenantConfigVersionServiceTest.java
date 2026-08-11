package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantConfigVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.entity.DmsTenantConfigVersion;
import com.macro.mall.distribution.entity.DmsTenantDisplayConfig;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import com.macro.mall.distribution.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConfigVersionServiceTest {

    @Test
    void createsBaselineForLegacyTenantAndRestoresBothSnapshots() throws Exception {
        DmsTenantDao tenantDao = mock(DmsTenantDao.class);
        DmsCommissionRuleVersionDao ruleVersionDao = mock(DmsCommissionRuleVersionDao.class);
        DmsTenantDisplayConfigDao displayDao = mock(DmsTenantDisplayConfigDao.class);
        DmsTenantConfigVersionDao versionDao = mock(DmsTenantConfigVersionDao.class);
        TenantLegalTemplateSupport legalSupport = mock(TenantLegalTemplateSupport.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TenantDisplayConfigSupport displaySupport = new TenantDisplayConfigSupport(objectMapper);
        TenantServiceImpl service = new TenantServiceImpl(tenantDao, ruleVersionDao, displayDao, versionDao,
                displaySupport, legalSupport, operationLogService, objectMapper);

        DmsTenant current = tenant(1L, "当前商城");
        DmsTenantDisplayConfig currentDisplay = display(1L, 0);
        DmsTenant restored = tenant(1L, "历史商城");
        DmsTenantDisplayConfig restoredDisplay = display(1L, 1);
        DmsTenantConfigVersion target = new DmsTenantConfigVersion();
        target.setId(9L);
        target.setTenantId(1L);
        target.setVersionNo("V-HISTORY");
        target.setTenantSnapshot(objectMapper.writeValueAsString(restored));
        target.setDisplaySnapshot(objectMapper.writeValueAsString(restoredDisplay));

        when(tenantDao.selectById(1L)).thenReturn(current);
        when(displayDao.selectByTenantId(1L)).thenReturn(currentDisplay);
        when(versionDao.countByTenantId(1L)).thenReturn(0, 1);
        when(versionDao.selectByIdAndTenantId(9L, 1L)).thenReturn(target);
        when(tenantDao.update(any(DmsTenant.class))).thenReturn(1);
        when(displayDao.update(any(DmsTenantDisplayConfig.class))).thenReturn(1);

        service.restoreConfigVersion(1L, 9L);

        ArgumentCaptor<DmsTenant> tenantUpdate = ArgumentCaptor.forClass(DmsTenant.class);
        verify(tenantDao).update(tenantUpdate.capture());
        assertEquals("历史商城", tenantUpdate.getValue().getBrandName());
        ArgumentCaptor<DmsTenantDisplayConfig> displayUpdate = ArgumentCaptor.forClass(DmsTenantDisplayConfig.class);
        verify(displayDao).update(displayUpdate.capture());
        assertEquals(1, displayUpdate.getValue().getShowPv());

        ArgumentCaptor<DmsTenantConfigVersion> versions = ArgumentCaptor.forClass(DmsTenantConfigVersion.class);
        verify(versionDao, times(3)).insert(versions.capture());
        assertEquals(List.of("BASELINE", "PRE_RESTORE", "RESTORE"),
                versions.getAllValues().stream().map(DmsTenantConfigVersion::getChangeType).toList());
        assertTrue(versions.getAllValues().stream().allMatch(item -> item.getTenantSnapshot() != null));
        verify(operationLogService).log(eq("TENANT_CONFIG"), eq("RESTORE"), eq("商城配置"), eq("1"),
                any(), eq("V-HISTORY"), any());
    }

    private DmsTenant tenant(Long id, String brandName) {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(id);
        tenant.setTenantCode("T001");
        tenant.setTenantName(brandName);
        tenant.setBrandName(brandName);
        tenant.setThemeColor("#e7193f");
        tenant.setProductTemplate("retail-red");
        tenant.setStatus(1);
        return tenant;
    }

    private DmsTenantDisplayConfig display(Long tenantId, int showPv) {
        DmsTenantDisplayConfig config = new DmsTenantDisplayConfig();
        config.setTenantId(tenantId);
        config.setShowPv(showPv);
        return config;
    }
}
