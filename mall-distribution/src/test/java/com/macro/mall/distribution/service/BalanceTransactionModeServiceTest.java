package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceTransactionModeServiceTest {

    private final DmsTenantDao tenants = mock(DmsTenantDao.class);
    private final BalanceTransactionModeService mode = new BalanceTransactionModeService(tenants);

    @Test
    void legacyMissingFlagRemainsEnabledButMissingTenantFailsClosed() {
        when(tenants.selectById(1L)).thenReturn(new DmsTenant());
        assertTrue(mode.isEnabled(1L));
        assertFalse(mode.isEnabled(2L));
    }

    @Test
    void transactionGateUsesLockedCurrentValueAfterClosure() {
        DmsTenant earlier = new DmsTenant(); earlier.setBalanceTransactionsEnabled(1);
        DmsTenant closed = new DmsTenant(); closed.setBalanceTransactionsEnabled(0);
        when(tenants.selectById(1L)).thenReturn(earlier);
        when(tenants.selectByIdForUpdate(1L)).thenReturn(closed);

        assertTrue(mode.isEnabled(1L));
        assertThrows(ApiException.class, () -> mode.requireEnabledForNewTransaction(1L));
        verify(tenants).selectByIdForUpdate(1L);
    }
}
