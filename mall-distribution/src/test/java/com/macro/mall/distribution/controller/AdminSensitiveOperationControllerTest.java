package com.macro.mall.distribution.controller;

import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAfterSaleApplyDTO;
import com.macro.mall.distribution.dto.ImportAgentDTO;
import com.macro.mall.distribution.dto.MerchantWithdrawalPayDTO;
import com.macro.mall.distribution.dto.WithdrawConfirmPayDTO;
import com.macro.mall.distribution.dto.WithdrawAuditDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.security.AdminContext;
import com.macro.mall.distribution.service.AdminAuthService;
import com.macro.mall.distribution.service.AdminMemberSecurityService;
import com.macro.mall.distribution.service.AdminUserService;
import com.macro.mall.distribution.service.ExternalTeamMigrationService;
import com.macro.mall.distribution.service.FlashSaleService;
import com.macro.mall.distribution.service.ImportService;
import com.macro.mall.distribution.service.LogisticsTrackingService;
import com.macro.mall.distribution.service.MerchantService;
import com.macro.mall.distribution.service.OrderRealtimeService;
import com.macro.mall.distribution.service.OrderShipmentService;
import com.macro.mall.distribution.service.OrderSpreadsheetService;
import com.macro.mall.distribution.service.ShopAddressService;
import com.macro.mall.distribution.service.ShopAfterSaleService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.ShopService;
import com.macro.mall.distribution.service.ShopServiceAddressService;
import com.macro.mall.distribution.service.TenantService;
import com.macro.mall.distribution.service.WithdrawService;
import com.macro.mall.distribution.service.WithdrawalPayoutService;
import com.macro.mall.distribution.vo.WithdrawalPayoutVO;
import com.macro.mall.distribution.security.ShopSessionCookieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void customerAfterSaleAndExceptionalRefundUseSeparateEntryTypes() {
        ShopAuthService authService = mock(ShopAuthService.class);
        ShopAfterSaleService afterSaleService = mock(ShopAfterSaleService.class);
        ShopController controller = controller(authService, mock(AdminAuthService.class), afterSaleService);
        DmsShopMember member = new DmsShopMember();
        when(authService.requireMember("member-token")).thenReturn(member);
        ShopAfterSaleApplyDTO dto = new ShopAfterSaleApplyDTO();
        dto.setOrderId(77L);

        dto.setApplyType(1);
        assertThrows(RuntimeException.class, () -> controller.applyAfterSale("member-token", dto));
        dto.setApplyType(4);
        assertThrows(RuntimeException.class, () -> controller.applyAfterSale("member-token", dto));
        dto.setApplyType(2);
        controller.applyAfterSale("member-token", dto);
        verify(afterSaleService).apply(member, dto);

        dto.setApplyType(4);
        assertThrows(RuntimeException.class, () -> controller.applyExceptionRefund("member-token", 78L, dto));
        controller.applyExceptionRefund("member-token", 77L, dto);
        verify(afterSaleService, org.mockito.Mockito.times(2)).apply(member, dto);
    }

    @Test
    void importAuditActorAlwaysComesFromAuthenticatedAdmin() {
        ImportService importService = mock(ImportService.class);
        ImportController controller = new ImportController(importService, mock(ExternalTeamMigrationService.class),
                mock(com.macro.mall.distribution.service.impl.ImportExecutionGuard.class));
        DmsAdminUser admin = admin(9L, "real_operator", "真实操作人");
        AdminContext.set(admin);
        List<ImportAgentDTO> rows = List.of(new ImportAgentDTO());

        controller.importAgentsByList(rows, 999L, "伪造操作人");

        verify(importService).importAgents(rows, 9L, "真实操作人");
    }

    @Test
    void agentWithdrawalPaymentUsesBusinessConfirmationWithoutRepeatedPassword() {
        WithdrawService withdrawService = mock(WithdrawService.class);
        WithdrawController controller = new WithdrawController(
                withdrawService, mock(com.macro.mall.distribution.service.WithdrawalPayoutService.class),
                mock(com.macro.mall.distribution.service.PerformanceService.class));
        WithdrawConfirmPayDTO dto = new WithdrawConfirmPayDTO();
        dto.setPayNo("BANK-20260821-001");

        controller.confirmPay(100L, dto);

        verify(withdrawService).confirmPay(100L, "BANK-20260821-001");
    }

    @Test
    void oneManualApprovalImmediatelyStartsOfficialPayout() {
        WithdrawService withdrawService = mock(WithdrawService.class);
        WithdrawalPayoutService payoutService = mock(WithdrawalPayoutService.class);
        WithdrawController controller = new WithdrawController(
                withdrawService, payoutService, mock(com.macro.mall.distribution.service.PerformanceService.class));
        AdminContext.set(admin(12L, "finance", "财务审核"));
        WithdrawAuditDTO dto = new WithdrawAuditDTO();
        dto.setId(200L);
        dto.setStatus(1);
        when(withdrawService.auditWithdraw(dto)).thenReturn(true);
        WithdrawalPayoutVO processing = new WithdrawalPayoutVO();
        processing.setState("PROCESSING");
        when(payoutService.start(200L)).thenReturn(processing);

        controller.auditWithdraw(dto);

        verify(payoutService).requireReady(200L);
        verify(withdrawService).auditWithdraw(dto);
        verify(payoutService).start(200L);
    }

    @Test
    void merchantWithdrawalPaymentUsesBusinessConfirmationWithoutRepeatedPassword() {
        MerchantService merchantService = mock(MerchantService.class);
        MerchantController controller = new MerchantController(merchantService);
        MerchantWithdrawalPayDTO dto = new MerchantWithdrawalPayDTO();
        dto.setActualPaidAmount(new BigDecimal("100.00"));
        dto.setPaymentReference("BANK-20260821-002");

        controller.pay(200L, dto);

        verify(merchantService).confirmPayment(200L, dto);
    }

    private ShopController controller(ShopAuthService shopAuthService, AdminAuthService adminAuthService) {
        return controller(shopAuthService, adminAuthService, mock(ShopAfterSaleService.class));
    }

    private ShopController controller(ShopAuthService shopAuthService, AdminAuthService adminAuthService,
                                      ShopAfterSaleService afterSaleService) {
        return new ShopController(
                mock(ShopService.class), shopAuthService, mock(ShopAddressService.class),
                mock(ShopServiceAddressService.class), afterSaleService, mock(TenantService.class),
                adminAuthService, mock(AdminUserService.class), mock(AdminMemberSecurityService.class), mock(OrderShipmentService.class),
                mock(OrderSpreadsheetService.class), mock(ShopSessionCookieService.class),
                mock(OrderRealtimeService.class), mock(FlashSaleService.class), mock(LogisticsTrackingService.class),
                mock(com.macro.mall.distribution.service.WeChatLogisticsQueryService.class),
                mock(com.macro.mall.distribution.service.WeChatExpressDeliveryService.class),
                mock(com.macro.mall.distribution.service.MerchantProductReviewService.class),
                mock(com.macro.mall.distribution.service.LiveRoomService.class));
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
