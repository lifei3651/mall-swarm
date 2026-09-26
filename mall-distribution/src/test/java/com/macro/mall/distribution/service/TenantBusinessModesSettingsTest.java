package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.macro.mall.common.api.ResultCode;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantConfigVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dao.DmsTenantDisplayConfigDao;
import com.macro.mall.distribution.dto.TenantBusinessModesDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.impl.TenantDisplayConfigSupport;
import com.macro.mall.distribution.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantBusinessModesSettingsTest {

    private DmsTenantDao tenantDao;
    private DmsTenantConfigVersionDao configVersionDao;
    private OperationLogService operationLogService;
    private ShopCatalogCacheService catalogCache;
    private AdminAuthService adminAuthService;
    private TenantServiceImpl service;
    private DmsAdminUser admin;

    @BeforeEach
    void setUp() {
        tenantDao = mock(DmsTenantDao.class);
        DmsCommissionRuleVersionDao ruleVersionDao = mock(DmsCommissionRuleVersionDao.class);
        DmsTenantDisplayConfigDao displayDao = mock(DmsTenantDisplayConfigDao.class);
        configVersionDao = mock(DmsTenantConfigVersionDao.class);
        TenantLegalTemplateSupport legalSupport = mock(TenantLegalTemplateSupport.class);
        operationLogService = mock(OperationLogService.class);
        catalogCache = mock(ShopCatalogCacheService.class);
        adminAuthService = mock(AdminAuthService.class);
        BrandCultureImagePolicy imagePolicy = mock(BrandCultureImagePolicy.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new TenantServiceImpl(tenantDao, ruleVersionDao, displayDao, configVersionDao,
                new TenantDisplayConfigSupport(objectMapper), legalSupport, operationLogService, objectMapper,
                catalogCache, adminAuthService, imagePolicy);

        admin = new DmsAdminUser();
        admin.setId(8L);
        admin.setUsername("bonus-admin");
        AdminContext.set(admin);
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void readsOnlyBusinessModeFieldsUnderBonusPermission() {
        DmsTenant tenant = tenant(1L, "商城主体不得外泄", "CUSTOM");
        when(tenantDao.selectById(1L)).thenReturn(tenant);

        TenantBusinessModesDTO result = service.getBusinessModes(1L);

        verify(adminAuthService).requirePermission(admin, "config:bonus");
        assertEquals(1L, result.getId());
        assertEquals("MANUAL_REVIEW", result.getPromotionJoinMode());
        assertEquals("STANDARD", result.getFlashSaleBonusMode());
        assertEquals("STANDARD", result.getRepurchaseBonusMode());
    }

    @Test
    void updatesOnlyBusinessModeColumnsAndNeverUsesWholeTenantUpdate() {
        DmsTenant before = tenant(1L, "不可被业务模式页面修改的主体", "NONE");
        DmsTenant saved = tenant(1L, "不可被业务模式页面修改的主体", "STANDARD");
        saved.setFlashSaleEnabled(1);
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(before);
        when(tenantDao.selectById(1L)).thenReturn(saved);
        when(configVersionDao.countByTenantId(1L)).thenReturn(1);
        when(tenantDao.updateBusinessModes(eq(1L), any(TenantBusinessModesDTO.class))).thenReturn(1);

        TenantBusinessModesDTO request = modes();
        request.setId(999L);
        request.setFlashSaleEnabled(1);
        request.setFlashSaleBonusMode("STANDARD");
        TenantBusinessModesDTO result = service.saveBusinessModes(1L, request);

        verify(adminAuthService).requirePermission(admin, "config:bonus");
        ArgumentCaptor<TenantBusinessModesDTO> update = ArgumentCaptor.forClass(TenantBusinessModesDTO.class);
        verify(tenantDao).updateBusinessModes(eq(1L), update.capture());
        assertEquals(1L, update.getValue().getId());
        assertEquals(1, update.getValue().getFlashSaleEnabled());
        assertEquals("STANDARD", update.getValue().getFlashSaleBonusMode());
        assertEquals(1, update.getValue().getCouponEnabled());
        verify(tenantDao, never()).update(any(DmsTenant.class));
        verify(catalogCache).invalidateAfterCommit(1L);
        verify(operationLogService).log(eq("TENANT_CONFIG"), eq("BUSINESS_MODE_UPDATE"), eq("TENANT"),
                eq("1"), any(), any(), any());
        assertEquals("不可被业务模式页面修改的主体", saved.getTenantName());
        assertEquals(1, result.getFlashSaleEnabled());
    }

    @Test
    void changingCouponModuleRequiresShopAndFinancePermissions() {
        DmsTenant before = tenant(1L, "商城", "NONE");
        DmsTenant saved = tenant(1L, "商城", "NONE");
        saved.setCouponEnabled(0);
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(before);
        when(tenantDao.selectById(1L)).thenReturn(saved);
        when(configVersionDao.countByTenantId(1L)).thenReturn(1);
        when(tenantDao.updateBusinessModes(eq(1L), any(TenantBusinessModesDTO.class))).thenReturn(1);
        TenantBusinessModesDTO request=modes(); request.setCouponEnabled(0);

        assertEquals(0, service.saveBusinessModes(1L,request).getCouponEnabled());
        verify(adminAuthService).requirePermission(admin,"config:shop");
        verify(adminAuthService).requirePermission(admin,"finance:manage");
        ArgumentCaptor<TenantBusinessModesDTO> update=ArgumentCaptor.forClass(TenantBusinessModesDTO.class);
        verify(tenantDao).updateBusinessModes(eq(1L),update.capture());
        assertEquals(0,update.getValue().getCouponEnabled());
    }

    @Test
    void rejectsInvalidCouponFlagBeforeWritingTenant() {
        TenantBusinessModesDTO request=modes(); request.setCouponEnabled(2);

        assertThrows(ApiException.class, () -> service.saveBusinessModes(1L, request));
        verify(tenantDao, never()).updateBusinessModes(eq(1L), any(TenantBusinessModesDTO.class));
    }

    @Test
    void rejectsMissingOrUnauthorizedAdminBeforeReadingTenant() {
        AdminContext.clear();
        ApiException missing = assertThrows(ApiException.class, () -> service.getBusinessModes(1L));
        assertEquals(ResultCode.FORBIDDEN, missing.getErrorCode());
        verify(tenantDao, never()).selectById(any());

        AdminContext.set(admin);
        doThrow(new ApiException(ResultCode.FORBIDDEN, "没有操作权限：config:bonus"))
                .when(adminAuthService).requirePermission(admin, "config:bonus");
        ApiException denied = assertThrows(ApiException.class, () -> service.getBusinessModes(1L));
        assertEquals(ResultCode.FORBIDDEN, denied.getErrorCode());
        verify(tenantDao, never()).selectById(any());
    }

    private TenantBusinessModesDTO modes() {
        TenantBusinessModesDTO modes = new TenantBusinessModesDTO();
        modes.setPromotionJoinMode("MANUAL_REVIEW");
        modes.setFlashSaleEnabled(0);
        modes.setFlashSaleBonusMode("NONE");
        modes.setRepurchaseMallEnabled(1);
        modes.setRepurchaseEligibilityMode("PAID_MEMBER");
        modes.setRepurchaseBonusMode("STANDARD");
        modes.setCouponEnabled(1);
        return modes;
    }

    private DmsTenant tenant(Long id, String tenantName, String bonusMode) {
        DmsTenant tenant = new DmsTenant();
        tenant.setId(id);
        tenant.setTenantCode("T001");
        tenant.setTenantName(tenantName);
        tenant.setBrandName("品牌");
        tenant.setPromotionJoinMode("MANUAL_REVIEW");
        tenant.setFlashSaleEnabled(0);
        tenant.setFlashSaleBonusMode(bonusMode);
        tenant.setRepurchaseMallEnabled(1);
        tenant.setRepurchaseEligibilityMode("PAID_MEMBER");
        tenant.setRepurchaseBonusMode(bonusMode);
        tenant.setCouponEnabled(1);
        tenant.setStatus(1);
        return tenant;
    }
}
