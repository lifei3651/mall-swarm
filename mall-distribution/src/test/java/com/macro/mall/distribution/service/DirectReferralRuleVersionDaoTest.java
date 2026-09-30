package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.bonus.CustomerBonusPolicyCodes;
import com.macro.mall.distribution.bonus.DirectReferralRuleConfig;
import com.macro.mall.distribution.dao.DmsCommissionRuleVersionDao;
import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectReferralRuleVersionDaoTest {
    @Autowired private DmsCommissionRuleVersionDao dao;
    @Autowired private ObjectMapper mapper;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void switchPreservesFrozenJsonAndDeactivatesOnlyCurrentTenant() throws Exception {
        TenantContext.setTenantId(71L);
        DmsCommissionRuleVersion original = version(71L, "0.1");
        dao.insert(original);
        DmsCommissionRuleVersion foreign = version(72L, "0.2");
        dao.insert(foreign);
        String snapshot = original.getDirectReferralConfig();

        assertEquals(1, dao.deactivateActive(71L));
        DmsCommissionRuleVersion updated = version(71L, "0.3");
        dao.insert(updated);

        assertEquals(updated.getId(), dao.selectActiveByTenantId(71L).getId());
        assertEquals(snapshot, dao.selectById(71L, original.getId()).getDirectReferralConfig());
        assertEquals(0, dao.selectById(71L, original.getId()).getStatus());
        assertEquals(1, dao.selectById(72L, foreign.getId()).getStatus());
        assertNull(dao.selectById(71L, foreign.getId()));
        assertEquals(updated.getId(), dao.selectActiveByTenantIdForUpdate(71L).getId());
        assertEquals(snapshot, dao.selectByIdForUpdate(71L, original.getId()).getDirectReferralConfig());
        assertEquals(0, dao.selectByIdForUpdate(71L, original.getId()).getStatus(),
                "当前读仍允许按指定版本读取已停用的历史不可变配置");
        assertNull(dao.selectByIdForUpdate(71L, foreign.getId()), "当前读必须保留租户隔离");
    }

    private DmsCommissionRuleVersion version(Long tenantId, String rate) throws Exception {
        DmsCommissionRuleVersion row = new DmsCommissionRuleVersion();
        row.setTenantId(tenantId); row.setVersionNo(CustomerBonusPolicyCodes.DIRECT_REFERRAL);
        row.setVersionName("直接推荐佣金"); row.setStatus(1); row.setEffectiveTime(LocalDateTime.now());
        row.setDirectReferralConfig(mapper.writeValueAsString(
                new DirectReferralRuleConfig(true, new BigDecimal(rate), "ALL_ORDERS", 7)));
        return row;
    }
}
