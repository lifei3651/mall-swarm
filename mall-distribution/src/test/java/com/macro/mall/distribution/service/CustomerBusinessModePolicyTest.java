package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dto.*;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.config.CustomerBusinessModeBoundaryConfig;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomerBusinessModePolicyTest {
    @Test void legacyNullPreservesExistingInvitationWhileBothExplicitModesRejectQualification() {
        DmsTenant tenant = new DmsTenant(); tenant.setInvitationEnabled(1);
        assertTrue(CustomerBusinessModePolicy.invitation(tenant));
        assertDoesNotThrow(() -> CustomerBusinessModePolicy.requireLegacyQualification(tenant));
        for (String mode : List.of("NORMAL", "AGENCY")) {
            tenant.setBusinessMode(mode);
            assertFalse(CustomerBusinessModePolicy.invitation(tenant));
            assertThrows(ApiException.class, () -> CustomerBusinessModePolicy.requireLegacyQualification(tenant));
        }
    }
    @Test void completeDraftNeverClaimsVerifiedOrEnablesAnUnimplementedRule() {
        DmsTenant tenant = new DmsTenant(); tenant.setBusinessMode("AGENCY");
        assertEquals("NOT_CONFIGURED", CustomerBusinessModePolicy.status(tenant).state());
        AgencyRuleDraft draft = new AgencyRuleDraft(); draft.setThresholdType("CUMULATIVE");
        tenant.setAgencyRuleDraft(CustomerBusinessModePolicy.encode(draft));
        assertEquals("INCOMPLETE", CustomerBusinessModePolicy.status(tenant).state());
        draft.setThresholdAmount(new BigDecimal("500.00")); draft.setOpeningMethod("MANUAL_REVIEW");
        draft.setTargetLevel(4); draft.setAttributionRule("客户待验证归属"); draft.setRefundRule("客户待验证退款"); draft.setRewardPolicy("客户制度草稿");
        tenant.setAgencyRuleDraft(CustomerBusinessModePolicy.encode(draft));
        assertEquals("PENDING_IMPLEMENTATION", CustomerBusinessModePolicy.status(tenant).state());
        assertTrue(CustomerBusinessModePolicy.status(tenant).missingItems().isEmpty());
        assertThrows(ApiException.class, () -> CustomerBusinessModePolicy.requireLegacyQualification(tenant));
    }
    @Test void specifiedProductDraftDoesNotBorrowAnAmountOrAssumeFirstOrder() {
        DmsTenant tenant = new DmsTenant(); tenant.setBusinessMode("AGENCY");
        AgencyRuleDraft d = new AgencyRuleDraft(); d.setThresholdType("SPECIFIED_PRODUCTS"); d.setThresholdAmount(BigDecimal.TEN);
        tenant.setAgencyRuleDraft(CustomerBusinessModePolicy.encode(d));
        assertTrue(CustomerBusinessModePolicy.status(tenant).missingItems().contains("购买金额或指定商品"));
        d.setProductIds(List.of(7L)); tenant.setAgencyRuleDraft(CustomerBusinessModePolicy.encode(d));
        assertFalse(CustomerBusinessModePolicy.status(tenant).missingItems().contains("购买金额或指定商品"));
    }
    @Test void clientsCannotAssignComputedStatusOrSubmitRawDraftJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var dto = mapper.readValue("{\"businessMode\":\"AGENCY\",\"agencyConfigStatus\":{\"state\":\"READY\"},\"modeRevision\":\"fake\",\"agencyRuleDraftJson\":\"{}\"}", TenantBusinessModesDTO.class);
        assertNull(dto.getAgencyConfigStatus()); assertNull(dto.getModeRevision()); assertNull(dto.getAgencyRuleDraft());
    }
    @Test void capabilityBoundaryExcludesHistoricalMoneyAndCoversActualTeamRoutes() {
        assertTrue(CustomerBusinessModeBoundaryConfig.restrictedPath("/distribution/agent/children/12"));
        assertTrue(CustomerBusinessModeBoundaryConfig.restrictedPath("/distribution/agent/qrcode/12"));
        assertTrue(CustomerBusinessModeBoundaryConfig.restrictedPath("/shop/profile/performance"));
        for (String p : List.of("/distribution/commission/settlement-batches", "/distribution/withdraw/1", "/shop/wallet/withdrawals", "/shop/after-sales", "/distribution/audit/finance"))
            assertFalse(CustomerBusinessModeBoundaryConfig.restrictedPath(p));
    }
}
