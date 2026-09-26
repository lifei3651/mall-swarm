package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.distribution.dto.MerchantControlDTO;
import com.macro.mall.distribution.dto.MerchantOnboardingDTO;
import com.macro.mall.distribution.dto.MerchantProductReviewCheckDTO;
import com.macro.mall.distribution.dto.MerchantProductReviewDecisionDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsMerchant;
import com.macro.mall.distribution.entity.DmsMerchantProductReview;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.security.AdminContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantPlatformOnlyModeTest {
    @Autowired private MerchantService merchantService;
    @Autowired private MerchantProductReviewService reviewService;
    @Autowired private ShopService shopService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAdmin() { AdminContext.clear(); }

    @Test
    void platformOnlyRejectsNewMerchantAndReactivationButKeepsHistoricalMerchantReadable() {
        DmsMerchant existing = merchant("M-PLATFORM-ONLY-1");
        closeMerchantMode();

        RuntimeException createError = assertThrows(RuntimeException.class,
                () -> merchant("M-PLATFORM-ONLY-2"));
        assertTrue(createError.getMessage().contains("仅支持平台自营"));

        // 带二次密码校验的正式入驻入口也必须落到同一个门禁。
        String password = "merchant-mode-test-password";
        jdbcTemplate.update("UPDATE dms_admin_user SET password_hash=?,salt='BCRYPT' WHERE id=1",
                BCrypt.hashpw(password));
        DmsAdminUser platformAdmin = admin(1L, "admin", "*", null);
        platformAdmin.setRoleCode("SUPER_ADMIN");
        AdminContext.set(platformAdmin);
        MerchantOnboardingDTO onboarding = new MerchantOnboardingDTO();
        onboarding.setMerchantNo("M-PLATFORM-ONLY-ONBOARD");
        onboarding.setMerchantName("禁止入驻测试商户");
        onboarding.setUsername("merchant_mode_closed");
        onboarding.setCurrentAdminPassword(password);
        RuntimeException onboardError = assertThrows(RuntimeException.class,
                () -> merchantService.onboardMerchant(onboarding));
        assertTrue(onboardError.getMessage().contains("仅支持平台自营"));
        AdminContext.clear();

        assertTrue(merchantService.listMerchants("M-PLATFORM-ONLY-1", null).stream()
                .anyMatch(item -> existing.getId().equals(item.getId())));
        assertTrue(merchantService.listAccounts(null).stream()
                .anyMatch(item -> existing.getId().equals(item.getMerchantId())));

        // 关模块不是冻结历史商户的处置：暂停仍可执行，恢复新的经营能力必须拒绝。
        assertTrue(merchantService.updateMerchantStatus(existing.getId(), 0));
        RuntimeException resumeError = assertThrows(RuntimeException.class,
                () -> merchantService.updateMerchantStatus(existing.getId(), 1));
        assertTrue(resumeError.getMessage().contains("仅支持平台自营"));

        MerchantControlDTO controls = new MerchantControlDTO();
        controls.setAccountStatus("ENABLED");
        controls.setBusinessStatus("ACTIVE");
        controls.setFulfillmentStatus("ENABLED");
        controls.setWithdrawalStatus("ENABLED");
        controls.setSettlementStatus("ENABLED");
        controls.setDepositStatus("NORMAL");
        controls.setAuditStatus("APPROVED");
        controls.setExitStatus("NORMAL");
        controls.setReason("测试另一恢复经营入口");
        RuntimeException controlsError = assertThrows(RuntimeException.class,
                () -> merchantService.updateMerchantControls(existing.getId(), controls));
        assertTrue(controlsError.getMessage().contains("仅支持平台自营"));
    }

    @Test
    void platformOnlyRejectsNewMerchantProductAndApprovalButAllowsHistoricalRejection() {
        DmsMerchant merchant = merchant("M-PLATFORM-ONLY-REVIEW");
        jdbcTemplate.update("""
                UPDATE dms_shop_product SET merchant_id=?,merchant_name=?,sale_price=99,cost_amount=50,
                    status=0,team_bonus_mode='NONE',merchant_review_status='DRAFT',merchant_review_version=0
                WHERE id=1
                """, merchant.getId(), merchant.getMerchantName());

        AdminContext.set(admin(8101L, "merchant_editor", "admin:read,shop:product", merchant));
        DmsMerchantProductReview pending = reviewService.submit(1L);
        closeMerchantMode();

        RuntimeException submitError = assertThrows(RuntimeException.class, () -> reviewService.submit(1L));
        assertTrue(submitError.getMessage().contains("仅支持平台自营"));
        DmsShopProduct newMerchantProduct = new DmsShopProduct();
        BeanUtils.copyProperties(shopService.getProduct(1L), newMerchantProduct);
        newMerchantProduct.setId(null);
        RuntimeException bindError = assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newMerchantProduct, null));
        assertTrue(bindError.getMessage().contains("仅支持平台自营"));

        AdminContext.set(admin(8102L, "platform_reviewer", "admin:read,shop:product-review", null));
        MerchantProductReviewDecisionDTO approval = decision(true, null);
        RuntimeException approvalError = assertThrows(RuntimeException.class,
                () -> reviewService.decide(pending.getId(), approval));
        assertTrue(approvalError.getMessage().contains("仅支持平台自营"));

        MerchantProductReviewDecisionDTO rejection = decision(false, "PRICE_SETTLEMENT");
        rejection.setRemark("平台改为仅自营，终止该商户商品审核");
        assertEquals("REJECTED", reviewService.decide(pending.getId(), rejection).getStatus());
    }

    @Test
    void newMerchantBindingRequiresExistingSameTenantAndApprovedActiveMerchant() {
        DmsMerchant merchant = merchant("M-PLATFORM-BIND-TARGET");
        DmsShopProduct newProduct = new DmsShopProduct();
        newProduct.setTenantId(1L);
        newProduct.setMerchantId(merchant.getId());
        assertDoesNotThrow(() -> reviewService.bindMerchantForWrite(newProduct, null));
        assertEquals(merchant.getMerchantName(), newProduct.getMerchantName());

        newProduct.setMerchantId(Long.MAX_VALUE);
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不存在"));
        newProduct.setMerchantId(merchant.getId());

        jdbcTemplate.update("UPDATE dms_merchant SET tenant_id=2 WHERE id=?", merchant.getId());
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不属于当前商城"));
        jdbcTemplate.update("UPDATE dms_merchant SET tenant_id=1,status=0 WHERE id=?", merchant.getId());
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不能绑定新商品"));

        jdbcTemplate.update("UPDATE dms_merchant SET status=1,business_status='SUSPENDED' WHERE id=?", merchant.getId());
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不能绑定新商品"));
        jdbcTemplate.update("UPDATE dms_merchant SET business_status='ACTIVE',audit_status='PENDING' WHERE id=?", merchant.getId());
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不能绑定新商品"));
        jdbcTemplate.update("UPDATE dms_merchant SET audit_status='APPROVED',exit_status='EXITING' WHERE id=?", merchant.getId());
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(newProduct, null)).getMessage().contains("不能绑定新商品"));

        // 商户工作台也不能通过省略 merchantId 绕过新商品绑定校验。
        AdminContext.set(admin(8103L, "merchant_editor", "admin:read,shop:product", merchant));
        DmsShopProduct merchantProduct = new DmsShopProduct();
        merchantProduct.setTenantId(1L);
        assertTrue(assertThrows(RuntimeException.class,
                () -> reviewService.bindMerchantForWrite(merchantProduct, null)).getMessage().contains("不能绑定新商品"));
    }

    private void closeMerchantMode() {
        assertEquals(1, jdbcTemplate.update("UPDATE dms_tenant SET multi_merchant_enabled=0 WHERE id=1"));
    }

    private DmsMerchant merchant(String no) {
        DmsMerchant merchant = new DmsMerchant();
        merchant.setMerchantNo(no);
        merchant.setMerchantName("平台模式测试商户");
        return merchantService.saveMerchant(merchant);
    }

    private DmsAdminUser admin(Long id, String username, String permissions, DmsMerchant merchant) {
        DmsAdminUser user = new DmsAdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setPermissions(permissions);
        if (merchant != null) {
            user.setMerchantId(merchant.getId());
            user.setMerchantName(merchant.getMerchantName());
        }
        return user;
    }

    private MerchantProductReviewDecisionDTO decision(boolean approved, String rejectedCode) {
        MerchantProductReviewDecisionDTO dto = new MerchantProductReviewDecisionDTO();
        dto.setApproved(approved);
        dto.setChecks(List.of("BASIC_INFO", "CATEGORY_QUALIFICATION", "CONTENT_COMPLIANCE",
                        "PRICE_SETTLEMENT", "STOCK_DELIVERY", "AFTER_SALE_PROMISE").stream().map(code -> {
            MerchantProductReviewCheckDTO item = new MerchantProductReviewCheckDTO();
            item.setCode(code);
            item.setPassed(!code.equals(rejectedCode));
            return item;
        }).toList());
        return dto;
    }
}
