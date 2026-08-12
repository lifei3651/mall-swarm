package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsTenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopBusinessModeServiceTest {

    private final DmsTenantDao tenantDao = mock(DmsTenantDao.class);
    private final DmsAgentDao agentDao = mock(DmsAgentDao.class);
    private final ShopBusinessModeService service = new ShopBusinessModeService(tenantDao, agentDao);

    @Test
    void optionalModesAreClosedByDefault() {
        when(tenantDao.selectById(1L)).thenReturn(new DmsTenant());
        assertEquals(0, service.config(1L, null).getFlashSaleEnabled());
        assertEquals(0, service.config(1L, null).getRepurchaseMallEnabled());
        assertThrows(ApiException.class, () -> service.requireEnabled(1L, ShopBusinessType.REPURCHASE, member()));
    }

    @Test
    void customBonusModeBlocksOrdersUntilCustomerRuleExists() {
        DmsTenant tenant = new DmsTenant();
        tenant.setRepurchaseMallEnabled(1);
        tenant.setRepurchaseEligibilityMode("ALL_MEMBER");
        tenant.setRepurchaseBonusMode("CUSTOM");
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        assertThrows(ApiException.class, () -> service.requireEnabled(1L, ShopBusinessType.REPURCHASE, member()));
    }

    @Test
    void paidMemberEligibilityRequiresAnActiveAgentRecord() {
        DmsTenant tenant = new DmsTenant();
        tenant.setRepurchaseMallEnabled(1);
        tenant.setRepurchaseEligibilityMode("PAID_MEMBER");
        tenant.setRepurchaseBonusMode("NONE");
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        when(agentDao.selectByUserId(8L)).thenReturn(null);
        assertFalse(service.config(1L, member()).getRepurchaseEligible());
        DmsAgent agent = new DmsAgent(); agent.setStatus(1); agent.setAgentLevel(1);
        when(agentDao.selectByUserId(8L)).thenReturn(agent);
        assertTrue(service.config(1L, member()).getRepurchaseEligible());
    }

    private DmsShopMember member() {
        DmsShopMember member = new DmsShopMember(); member.setUserId(8L); member.setStatus(1); return member;
    }
}
