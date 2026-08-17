package com.macro.mall.distribution.controller;

import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.AdminMemberSecurityService;
import com.macro.mall.distribution.service.ExternalTeamMigrationService;
import com.macro.mall.distribution.service.FlashSaleService;
import com.macro.mall.distribution.service.ImportService;
import com.macro.mall.distribution.service.LogisticsTrackingService;
import com.macro.mall.distribution.service.OrderRealtimeService;
import com.macro.mall.distribution.service.OrderShipmentService;
import com.macro.mall.distribution.service.OrderSpreadsheetService;
import com.macro.mall.distribution.service.ShopAddressService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.ShopServiceAddressService;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.security.ShopSessionCookieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdminSensitiveOperationControllerTest {

    @AfterEach
    void clearAdmin() {
        AdminContext.clear();
    }

    @Test
    void creatingDistributionMemberRequiresDistributionManagePermission() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        ShopAuthService shopAuthService = mock(ShopAuthService.class);
        ShopController controller = controller(shopAuthService, adminAuthService);
        DmsAdminUser admin = admin(7L, "member_operator", "会员运营");
        AdminContext.set(admin);
        AdminMemberCreateDTO dto = new AdminMemberCreateDTO();
        dto.setActivateDistribution(true);
        dto.setInitialLevel(8);

        controller.createAdminMember(dto);

        verify(adminAuthService).requirePermission(admin, "distribution:manage");
        verify(shopAuthService).createAdminMember(dto);
    }

    @Test
    void creatingPlainMemberKeepsShopMemberWorkflow() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        ShopAuthService shopAuthService = mock(ShopAuthService.class);
        ShopController controller = controller(shopAuthService, adminAuthService);
        AdminContext.set(admin(8L, "plain_member_operator", "普通会员运营"));
        AdminMemberCreateDTO dto = new AdminMemberCreateDTO();
        dto.setActivateDistribution(false);

        controller.createAdminMember(dto);

        verify(adminAuthService, never()).requirePermission(any(), any());
        verify(shopAuthService).createAdminMember(dto);
    }

    @Test
    void importAuditActorAlwaysComesFromAuthenticatedAdmin() {
        ImportService importService = mock(ImportService.class);
        ImportController controller = new ImportController(importService, mock(ExternalTeamMigrationService.class));
        DmsAdminUser admin = admin(9L, "real_operator", "真实操作人");
        AdminContext.set(admin);
        List<ImportAgentDTO> rows = List.of(new ImportAgentDTO());

        controller.importAgentsByList(rows, 999L, "伪造操作人");

        verify(importService).importAgents(rows, 9L, "真实操作人");
    }

    private ShopController controller(ShopAuthService shopAuthService, AdminAuthService adminAuthService) {
        return new ShopController(
                mock(ShopService.class), shopAuthService, mock(ShopAddressService.class),
                mock(ShopServiceAddressService.class), mock(ShopAfterSaleService.class), mock(TenantService.class),
                adminAuthService, mock(AdminMemberSecurityService.class), mock(OrderShipmentService.class),
                mock(OrderSpreadsheetService.class), mock(ShopSessionCookieService.class),
                mock(OrderRealtimeService.class), mock(FlashSaleService.class), mock(LogisticsTrackingService.class),
                mock(com.macro.mall.distribution.service.MerchantProductReviewService.class));
    }

    private DmsAdminUser admin(Long id, String username, String nickname) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id);
        admin.setUsername(username);
        admin.setNickname(nickname);
        admin.setStatus(1);
        return admin;
    }
}
