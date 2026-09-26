package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.constants.ShopBusinessType;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopBusinessConfigVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 service-boundary matrix, not a claim of five-module end-to-end or MySQL concurrency coverage.
 * Every switch combination must preserve the independent public flags and new-transaction gates.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantModuleCombinationMatrixTest {
    private static final Long TENANT_ID = 1L;
    private static final int FLASH = 1;
    private static final int REPURCHASE = 2;
    private static final int COUPON = 4;
    private static final int BALANCE = 8;
    private static final int MERCHANT = 16;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ShopService shopService;
    @Autowired private ShopBusinessModeService businessModes;
    @Autowired private ShopCouponService couponService;
    @Autowired private BalanceTransactionModeService balanceModes;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void allThirtyTwoModuleCombinationsKeepPublicFlagsAndNewSaleGatesIndependent() {
        DmsShopMember member = eligibleMember();
        for (int mask = 0; mask < 32; mask++) {
            setModes(mask);
            String caseName = "module mask " + mask;
            ShopBusinessConfigVO config = shopService.getBusinessConfig(member);
            assertEquals(flag(mask, FLASH), config.getFlashSaleEnabled(), caseName + " flash flag");
            assertEquals(flag(mask, REPURCHASE), config.getRepurchaseMallEnabled(), caseName + " repurchase flag");
            assertEquals(flag(mask, COUPON), config.getCouponEnabled(), caseName + " coupon flag");
            assertEquals(flag(mask, BALANCE), config.getBalanceTransactionsEnabled(), caseName + " balance flag");
            assertEquals(flag(mask, MERCHANT), config.getMultiMerchantEnabled(), caseName + " merchant flag");
            assertEquals(has(mask, REPURCHASE), config.getRepurchaseEligible(), caseName + " eligibility");

            // Normal platform sales are not an optional module and must remain available.
            assertDoesNotThrow(() -> businessModes.requireEnabledForOrder(TENANT_ID, ShopBusinessType.NORMAL, member),
                    caseName + " platform normal sale");
            assertGate(has(mask, FLASH), () -> businessModes.requireEnabledForOrder(
                    TENANT_ID, ShopBusinessType.FLASH_SALE, member), caseName + " flash sale");
            assertGate(has(mask, REPURCHASE), () -> businessModes.requireEnabledForOrder(
                    TENANT_ID, ShopBusinessType.REPURCHASE, member), caseName + " repurchase sale");
            assertEquals(has(mask, COUPON), couponService.isEnabled(), caseName + " coupon service flag");
            if (!has(mask, COUPON)) {
                assertThrows(ApiException.class, () -> couponService.reserve(member, 1L, 1L,
                        List.of(), ShopBusinessType.NORMAL), caseName + " coupon reservation");
            }
            assertEquals(has(mask, BALANCE), balanceModes.isEnabled(TENANT_ID), caseName + " balance service flag");
            assertGate(has(mask, BALANCE), () -> balanceModes.requireEnabledForNewTransaction(TENANT_ID),
                    caseName + " new balance transaction");
            assertEquals(has(mask, MERCHANT), businessModes.isMultiMerchantEnabled(TENANT_ID),
                    caseName + " merchant service flag");
            assertGate(has(mask, MERCHANT), () -> businessModes.requireMultiMerchantForNewSale(TENANT_ID),
                    caseName + " new merchant sale");
        }
    }

    @Test
    void allModulesClosedDoesNotBlockHistoricalPendingPlatformOrderPaymentCallback() {
        DmsShopMember member = persistedBuyer();
        ShopOrderVO pending = shopService.submitOrder(platformOrder(), member);
        Long orderId = pending.getOrder().getId();
        assertEquals(0, pending.getOrder().getStatus());

        setModes(0);

        assertEquals(1, shopService.markOrderPaid(orderId, "ALIPAY").getOrder().getStatus());
        assertEquals(1, shopService.getOrder(orderId).getOrder().getStatus());
        assertFalse(businessModes.isMultiMerchantEnabled(TENANT_ID));
        assertFalse(balanceModes.isEnabled(TENANT_ID));
        assertTrue(shopService.getProductDetail(2L) != null);
    }

    private void setModes(int mask) {
        assertEquals(1, jdbcTemplate.update("""
                UPDATE dms_tenant
                   SET flash_sale_enabled=?, repurchase_mall_enabled=?, coupon_enabled=?,
                       balance_transactions_enabled=?, multi_merchant_enabled=?,
                       flash_sale_bonus_mode='STANDARD', repurchase_bonus_mode='STANDARD',
                       repurchase_eligibility_mode='ALL_MEMBER'
                 WHERE id=?
                """, flag(mask, FLASH), flag(mask, REPURCHASE), flag(mask, COUPON),
                flag(mask, BALANCE), flag(mask, MERCHANT), TENANT_ID));
    }

    private static boolean has(int mask, int bit) {
        return (mask & bit) != 0;
    }

    private static int flag(int mask, int bit) {
        return has(mask, bit) ? 1 : 0;
    }

    private static void assertGate(boolean enabled, Runnable gate, String label) {
        if (enabled) assertDoesNotThrow(gate::run, label);
        else assertThrows(ApiException.class, gate::run, label);
    }

    private DmsShopMember eligibleMember() {
        DmsShopMember member = new DmsShopMember();
        member.setId(991730L);
        member.setUserId(991730L);
        member.setStatus(1);
        return member;
    }

    private DmsShopMember persistedBuyer() {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                    (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (991730,991730,'13900001730','mode_matrix_buyer','hash','模块矩阵测试会员','MM991730',1,0,0)
                """);
        DmsShopMember member = eligibleMember();
        member.setPhone("13900001730");
        member.setUsername("mode_matrix_buyer");
        return member;
    }

    private ShopOrderSubmitDTO platformOrder() {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(2L);
        item.setSkuId(3L);
        item.setQuantity(1);
        ShopOrderSubmitDTO order = new ShopOrderSubmitDTO();
        order.setItems(List.of(item));
        order.setReceiverName("测试收货人");
        order.setReceiverPhone("13900001730");
        order.setReceiverProvince("湖南省");
        order.setReceiverCity("长沙市");
        order.setReceiverDistrict("岳麓区");
        order.setReceiverDetailAddress("测试路一号");
        order.setReceiverAddress("湖南省长沙市岳麓区测试路一号");
        order.setPayType("ALIPAY");
        return order;
    }
}
