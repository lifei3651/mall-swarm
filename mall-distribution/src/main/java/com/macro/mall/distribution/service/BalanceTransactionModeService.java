package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Controls only new discretionary balance transactions, never historical ledger or liability settlement. */
@Service
@RequiredArgsConstructor
public class BalanceTransactionModeService {

    private final DmsTenantDao tenantDao;

    public boolean isEnabled(Long tenantId) {
        DmsTenant tenant = tenantDao.selectById(tenantId);
        return tenant != null && !Integer.valueOf(0).equals(tenant.getBalanceTransactionsEnabled());
    }

    /**
     * The caller must be in a transaction. This shares the tenant row lock with business-mode edits,
     * so a committed disable cannot be overtaken by a new payment, transfer or manual adjustment.
     */
    public void requireEnabledForNewTransaction(Long tenantId) {
        DmsTenant tenant = tenantDao.selectByIdForUpdate(tenantId);
        if (tenant == null) Asserts.fail("商城客户不存在");
        if (Integer.valueOf(0).equals(tenant.getBalanceTransactionsEnabled())) {
            Asserts.fail("本商城已关闭新余额交易");
        }
    }
}
