package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.bonus.DirectReferralRuleConfig;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.dto.DirectReferralConfigDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.impl.DirectReferralConfigServiceImpl;
import com.macro.mall.distribution.vo.DirectReferralConfigVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectReferralConfigServiceTest {
    private DmsTenantDao tenantDao;
    private DmsCommissionRuleVersionDao versionDao;
    private AdminAuthService auth;
    private OperationLogService log;
    private DirectReferralConfigServiceImpl service;
    private DmsAdminUser admin;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tenantDao = mock(DmsTenantDao.class);
        versionDao = mock(DmsCommissionRuleVersionDao.class);
        auth = mock(AdminAuthService.class);
        log = mock(OperationLogService.class);
        service = new DirectReferralConfigServiceImpl(tenantDao, versionDao, auth, log, mapper);
        admin = new DmsAdminUser(); admin.setId(1L);
        AdminContext.set(admin); TenantContext.setTenantId(4L);
    }

    @AfterEach
    void clearContexts() {
        AdminContext.clear(); TenantContext.clear();
    }

    @Test
    void freshBaseReadsDisabledWithoutCreatingOrChangingAnyRule() {
        DirectReferralConfigVO result = service.current();
        assertFalse(result.getEnabled());
        assertEquals(BigDecimal.ZERO, result.getCommissionRate());
        assertEquals("ALL_ORDERS", result.getPurchaseScope());
        assertEquals(7, result.getSettlementDelayDays());
        assertFalse(result.getReadOnly());
        verify(versionDao).selectActiveByTenantId(4L);
        verify(versionDao, never()).insert(any());
        verifyNoInteractions(tenantDao);
    }

    @Test
    void legacyPolicyIsReadOnlyAndRequiresExplicitVersionAwareSwitch() {
        DmsCommissionRuleVersion legacy = version(12L, "CUSTOMER_A_V2", null);
        when(versionDao.selectActiveByTenantId(4L)).thenReturn(legacy);
        when(versionDao.selectActiveByTenantIdForUpdate(4L)).thenReturn(legacy);
        assertTrue(service.current().getReadOnly());
        assertEquals("CUSTOMER_A_V2", service.current().getCurrentPolicyCode());
        when(tenantDao.selectByIdForUpdate(4L)).thenReturn(new DmsTenant());
        DirectReferralConfigDTO dto = request(); dto.setExpectedVersionId(12L);
        assertThrows(ApiException.class, () -> service.save(dto));
        verify(versionDao, never()).deactivateActive(anyLong());
        dto.setConfirmPolicySwitch(true);
        when(versionDao.insert(any())).thenAnswer(invocation -> {
            ((DmsCommissionRuleVersion) invocation.getArgument(0)).setId(13L); return 1;
        });
        DirectReferralConfigVO saved = service.save(dto);
        assertEquals(13L, saved.getVersionId());
        assertEquals("DIRECT_REFERRAL_V1", saved.getCurrentPolicyCode());
        assertFalse(saved.getReadOnly());
        assertNull(legacy.getDirectReferralConfig());
        assertEquals("CUSTOMER_A_V2", legacy.getVersionNo());
        verify(auth, times(2)).requirePermission(admin, "finance:manage");
    }

    @Test
    void staleSaveCannotDeactivateCurrentVersion() {
        when(tenantDao.selectByIdForUpdate(4L)).thenReturn(new DmsTenant());
        when(versionDao.selectActiveByTenantIdForUpdate(4L)).thenReturn(version(13L, CustomerBonusPolicyCodes.DISABLED, null));
        DirectReferralConfigDTO dto = request(); dto.setExpectedVersionId(12L);
        assertThrows(ApiException.class, () -> service.save(dto));
        verify(versionDao, never()).deactivateActive(anyLong());
        verify(versionDao, never()).insert(any());
    }

    @Test
    void saveInsertsImmutableRuleAndDoesNotChangeInvitationOrBuyerQualifications() throws Exception {
        when(tenantDao.selectByIdForUpdate(4L)).thenReturn(new DmsTenant());
        when(versionDao.insert(any())).thenReturn(1);
        service.save(request());
        ArgumentCaptor<DmsCommissionRuleVersion> saved = ArgumentCaptor.forClass(DmsCommissionRuleVersion.class);
        verify(versionDao).insert(saved.capture());
        assertEquals(4L, saved.getValue().getTenantId());
        assertEquals("DIRECT_REFERRAL_V1", saved.getValue().getVersionNo());
        DirectReferralRuleConfig frozen = mapper.readValue(saved.getValue().getDirectReferralConfig(), DirectReferralRuleConfig.class);
        assertEquals(new BigDecimal("0.075"), frozen.commissionRate());
        assertEquals(7, frozen.settlementDelayDays());
        verify(tenantDao, never()).update(any());
        verify(tenantDao, never()).updateBusinessModes(anyLong(), any());
        verify(log).log(eq("BONUS_CONFIG"), eq("DIRECT_REFERRAL_VERSION"), eq("TENANT"), eq("4"), any(), any(), any());
    }

    @Test
    void disablingCreatesNewDisabledVersionWhileOldRuleRemainsReadable() throws Exception {
        String oldConfig = mapper.writeValueAsString(new DirectReferralRuleConfig(true, new BigDecimal("0.1"), "ALL_ORDERS", 7));
        DmsCommissionRuleVersion old = version(12L, "DIRECT_REFERRAL_V1", oldConfig);
        old.setStatus(0);
        when(versionDao.selectByIdForUpdate(4L, 12L)).thenReturn(old);
        assertEquals(new BigDecimal("0.1"), service.frozen(4L, 12L).commissionRate());
        verify(versionDao).selectByIdForUpdate(4L, 12L);
        verify(versionDao, never()).selectById(anyLong(), anyLong());
        verify(versionDao, never()).selectActiveByTenantId(anyLong());

        when(tenantDao.selectByIdForUpdate(4L)).thenReturn(new DmsTenant());
        when(versionDao.selectActiveByTenantIdForUpdate(4L)).thenReturn(old);
        when(versionDao.insert(any())).thenReturn(1);
        DirectReferralConfigDTO dto = request(); dto.setEnabled(false); dto.setExpectedVersionId(12L);
        assertFalse(service.save(dto).getEnabled());
        ArgumentCaptor<DmsCommissionRuleVersion> inserted = ArgumentCaptor.forClass(DmsCommissionRuleVersion.class);
        verify(versionDao).insert(inserted.capture());
        assertEquals(CustomerBonusPolicyCodes.DISABLED, inserted.getValue().getVersionNo());
        assertEquals(oldConfig, old.getDirectReferralConfig());
    }

    @Test
    void frozenLookupNeverFallsBackOnMissingForeignOrCorruptVersion() {
        assertThrows(ApiException.class, () -> service.frozen(4L, 12L));
        DmsCommissionRuleVersion foreign = version(12L, "DIRECT_REFERRAL_V1", "{}");
        foreign.setTenantId(5L); when(versionDao.selectByIdForUpdate(4L, 12L)).thenReturn(foreign);
        assertThrows(ApiException.class, () -> service.frozen(4L, 12L));
        foreign.setTenantId(4L);
        assertThrows(ApiException.class, () -> service.frozen(4L, 12L));
        foreign.setDirectReferralConfig("malformed");
        assertThrows(ApiException.class, () -> service.frozen(4L, 12L));
        verify(versionDao, never()).selectActiveByTenantId(anyLong());
    }

    @Test
    void merchantAndMissingFinancialPermissionCannotChangePolicy() {
        admin.setMerchantId(22L);
        assertThrows(ApiException.class, () -> service.current());
        assertThrows(ApiException.class, () -> service.save(request()));
        verifyNoInteractions(tenantDao, versionDao);
        admin.setMerchantId(null);
        doThrow(new ApiException("财务权限不足")).when(auth).requirePermission(admin, "finance:manage");
        assertThrows(ApiException.class, () -> service.save(request()));
        verifyNoInteractions(tenantDao, versionDao);
    }

    @Test
    void monetaryAndScopeBoundariesRejectWithoutWriting() {
        for (String rate : new String[]{"-0.01", "1.0001", "0.12345", "0"}) {
            DirectReferralConfigDTO dto = request(); dto.setCommissionRate(new BigDecimal(rate));
            assertThrows(ApiException.class, () -> service.save(dto));
        }
        DirectReferralConfigDTO invalidScope = request(); invalidScope.setPurchaseScope("SECOND_ORDER");
        assertThrows(ApiException.class, () -> service.save(invalidScope));
        DirectReferralConfigDTO dto = request(); dto.setSettlementDelayDays(366); DirectReferralConfigDTO tooLong = dto;
        assertThrows(ApiException.class, () -> service.save(tooLong));
        dto = request(); dto.setSettlementDelayDays(-1); DirectReferralConfigDTO negative = dto;
        assertThrows(ApiException.class, () -> service.save(negative));
        verifyNoInteractions(tenantDao, versionDao);
        assertEquals(new BigDecimal("1"), new DirectReferralRuleConfig(true, new BigDecimal("1.0000"), "ALL_ORDERS", 365)
                .validated().commissionRate());
    }

    private DirectReferralConfigDTO request() {
        DirectReferralConfigDTO dto = new DirectReferralConfigDTO();
        dto.setEnabled(true); dto.setCommissionRate(new BigDecimal("0.075"));
        dto.setPurchaseScope("ALL_ORDERS"); dto.setSettlementDelayDays(7);
        return dto;
    }

    private DmsCommissionRuleVersion version(Long id, String policy, String json) {
        DmsCommissionRuleVersion version = new DmsCommissionRuleVersion();
        version.setId(id); version.setTenantId(4L); version.setVersionNo(policy); version.setStatus(1);
        version.setDirectReferralConfig(json); return version;
    }
}
