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
    void orderGateReadsTheLockedCurrentFlagNotAnEarlierOpenSnapshot() {
        DmsTenant earlier = new DmsTenant();
        earlier.setRepurchaseMallEnabled(1);
        earlier.setRepurchaseEligibilityMode("ALL_MEMBER");
        DmsTenant closed = new DmsTenant();
        closed.setRepurchaseMallEnabled(0);
        when(tenantDao.selectById(1L)).thenReturn(earlier);
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(closed);

        assertNotNull(service.requireEnabled(1L, ShopBusinessType.REPURCHASE, member()));
        assertThrows(ApiException.class,
                () -> service.requireEnabledForOrder(1L, ShopBusinessType.REPURCHASE, member()));

        verify(tenantDao).selectByIdForUpdate(1L);
    }

    @Test
    void normalOrdersDoNotTakeTheOptionalModuleHotRowLock() {
        when(tenantDao.selectById(1L)).thenReturn(new DmsTenant());

        assertNotNull(service.requireEnabledForOrder(1L, ShopBusinessType.NORMAL, member()));

        verify(tenantDao).selectById(1L);
        verify(tenantDao, never()).selectByIdForUpdate(1L);
    }

    @Test
    void flashSaleOrderGateAlsoUsesTheLockedCurrentFlag() {
        DmsTenant closed = new DmsTenant();
        closed.setFlashSaleEnabled(0);
        when(tenantDao.selectByIdForUpdate(1L)).thenReturn(closed);

        assertThrows(ApiException.class,
                () -> service.requireEnabledForOrder(1L, ShopBusinessType.FLASH_SALE, member()));

        verify(tenantDao).selectByIdForUpdate(1L);
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
