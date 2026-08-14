package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dto.ShopAfterSaleAuditDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopAfterSaleActorSecurityTest {

    @AfterEach
    void clearContext() { AdminContext.clear(); }

    @Test
    void overwritesClientSuppliedAuditIdentityWithAuthenticatedAdministrator() {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(19L);
        admin.setUsername("real-admin");
        admin.setNickname("真实管理员");
        AdminContext.set(admin);
        ShopAfterSaleAuditDTO dto = new ShopAfterSaleAuditDTO();
        dto.setAuditUserId(999L);
        dto.setAuditUserName("伪造操作人");

        ShopAfterSaleServiceImpl.applyAuthenticatedAdmin(dto);

        assertEquals(19L, dto.getAuditUserId());
        assertEquals("真实管理员", dto.getAuditUserName());
    }
}
