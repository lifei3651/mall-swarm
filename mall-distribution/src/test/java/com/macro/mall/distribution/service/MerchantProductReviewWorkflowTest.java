package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.MerchantProductReviewDecisionDTO;
import com.macro.mall.distribution.dto.MerchantProductReviewCheckDTO;
import com.macro.mall.distribution.dto.MerchantControlDTO;
import com.macro.mall.distribution.dto.MerchantProfileSubmitDTO;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MerchantProductReviewWorkflowTest {
    @Autowired private MerchantService merchantService;
    @Autowired private MerchantProductReviewService reviewService;
    @Autowired private ShopService shopService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAdmin() { AdminContext.clear(); }

    @Test
    void merchantDraftApprovalDelistPriceChangeAndRejectionFormClosedWorkflow() {
        DmsMerchant merchant = merchant("M-REVIEW-1", "商品审核测试商户");
        jdbcTemplate.update("""
                UPDATE dms_shop_product
                SET merchant_id=?,merchant_name=?,sale_price=99,cost_amount=50,status=0,
                    team_bonus_mode='NONE',merchant_review_status='DRAFT',merchant_review_version=0
                WHERE id=1
                """, merchant.getId(), merchant.getMerchantName());

        AdminContext.set(admin(7001L, "merchant_editor", "商户编辑", "admin:read,shop:product", merchant));
        DmsMerchantProductReview first = reviewService.submit(1L);
        assertEquals("PENDING", first.getStatus());
        assertEquals(new BigDecimal("99.00"), first.getSalePrice());
        assertEquals(new BigDecimal("50.00"), first.getSettlementPrice());
        assertEquals(0, shopService.getProduct(1L).getStatus());
        assertTrue(shopService.listProducts(1L, null, null, null, null).stream().noneMatch(item -> item.getId().equals(1L)));

        MerchantProductReviewDecisionDTO approve = new MerchantProductReviewDecisionDTO();
        approve.setApproved(true);
        approve.setChecks(reviewChecks(null));
        AdminContext.set(admin(7002L, "product_reviewer", "商品审核员", "admin:read,shop:product-review", null));
        DmsMerchantProductReview approved = reviewService.decide(first.getId(), approve);
        assertEquals("APPROVED", approved.getStatus());
        DmsShopProduct listed = shopService.getProduct(1L);
        assertEquals(1, listed.getStatus());
        assertEquals("APPROVED", listed.getMerchantReviewStatus());

        AdminContext.set(admin(7001L, "merchant_editor", "商户编辑", "admin:read,shop:product", merchant));
        assertThrows(RuntimeException.class, () -> shopService.updateSkuStatus(1L, 0));
        DmsShopProduct illegalEdit = copy(listed);
        illegalEdit.setSalePrice(new BigDecimal("109"));
        assertThrows(RuntimeException.class, () -> shopService.updateProduct(1L, illegalEdit));
        assertTrue(shopService.updateProductStatus(1L, 0));

        DmsShopProduct changed = copy(shopService.getProduct(1L));
        changed.setSalePrice(new BigDecimal("109"));
        changed.setCostAmount(new BigDecimal("55"));
        changed.setPvValue(BigDecimal.ZERO);
        changed.setDeliveryProvince("湖南省");
        changed.setDeliveryCity("长沙市");
        changed.setDeliveryDistrict("岳麓区");
        changed.setDeliveryAddress("湖南省 长沙市 岳麓区");
        DmsShopProduct draft = shopService.updateProduct(1L, changed);
        assertEquals(0, draft.getStatus());
        assertEquals("DRAFT", draft.getMerchantReviewStatus());
        DmsMerchantProductReview second = reviewService.submit(1L);
        assertEquals(2, second.getReviewVersion());
        assertEquals(new BigDecimal("55.00"), second.getSettlementPrice());

        MerchantProductReviewDecisionDTO reject = new MerchantProductReviewDecisionDTO();
        reject.setApproved(false);
        reject.setRemark("结算价依据不完整，请补充合同后重新提交");
        reject.setChecks(reviewChecks("PRICE_SETTLEMENT"));
        AdminContext.set(admin(7002L, "product_reviewer", "商品审核员", "admin:read,shop:product-review", null));
        reviewService.decide(second.getId(), reject);
        DmsShopProduct rejected = shopService.getProduct(1L);
        assertEquals(0, rejected.getStatus());
        assertEquals("REJECTED", rejected.getMerchantReviewStatus());
        assertTrue(rejected.getMerchantReviewRemark().contains("依据不完整"));
        assertThrows(RuntimeException.class, () -> reviewService.decide(second.getId(), reject));

        AdminContext.set(admin(7001L, "merchant_editor", "商户编辑", "admin:read,shop:product", merchant));
        DmsShopProduct lossMaking = copy(rejected);
        lossMaking.setCostAmount(new BigDecimal("120"));
        lossMaking.setDeliveryProvince("湖南省");
        lossMaking.setDeliveryCity("长沙市");
        lossMaking.setDeliveryDistrict("岳麓区");
        lossMaking.setDeliveryAddress("湖南省 长沙市 岳麓区");
        RuntimeException invalidSettlement = assertThrows(RuntimeException.class,
                () -> shopService.updateProduct(1L, lossMaking));
        assertTrue(invalidSettlement.getMessage().contains("结算价不能高于"));
    }

    @Test
    void merchantAccountCannotReadOrChangeAnotherMerchantProduct() {
        DmsMerchant owner = merchant("M-REVIEW-OWNER", "商品所有商户");
        DmsMerchant other = merchant("M-REVIEW-OTHER", "其他商户");
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=?,status=0,team_bonus_mode='NONE',merchant_review_status='DRAFT' WHERE id=1",
                owner.getId(), owner.getMerchantName());
        AdminContext.set(admin(7010L, "other_merchant", "其他商户", "admin:read,shop:product", other));
        assertThrows(RuntimeException.class, () -> shopService.getProduct(1L));
        assertThrows(RuntimeException.class, () -> reviewService.submit(1L));
        assertTrue(shopService.listAdminProductPage(1L, null, null, null, null, 1, 20).getList().isEmpty());
    }

    @Test
    void merchantMustSubmitCompleteProfileAndPassPlatformCertificationBeforeProductReview() {
        DmsMerchant merchant = merchant("M-ONBOARD-1", "入驻认证测试商户");
        jdbcTemplate.update("UPDATE dms_merchant SET status=0,business_status='SUSPENDED',audit_status='PENDING' WHERE id=?", merchant.getId());
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=?,merchant_name=?,sale_price=99,cost_amount=50,status=0,team_bonus_mode='NONE',merchant_review_status='DRAFT',merchant_review_version=0 WHERE id=1",
                merchant.getId(), merchant.getMerchantName());

        AdminContext.set(admin(7021L, "onboarding_merchant", "入驻商户", "admin:read,shop:product", merchant));
        MerchantProfileSubmitDTO profile = new MerchantProfileSubmitDTO();
        profile.setContactName("商户联系人"); profile.setContactPhone("13800138000");
        profile.setLegalEntityName("入驻认证测试商户有限公司"); profile.setUnifiedSocialCreditCode("91350100M000100Y43");
        profile.setBankAccountName("入驻认证测试商户有限公司"); profile.setBankName("测试银行测试支行"); profile.setBankAccountNo("6222021234567890123");
        profile.setInvoiceTitle("入驻认证测试商户有限公司"); profile.setTaxpayerIdentificationNo("91350100M000100Y43");
        DmsMerchant pending = merchantService.submitCurrentMerchantProfile(profile);
        assertEquals("PENDING", pending.getAuditStatus());
        assertEquals(0, pending.getStatus());
        assertThrows(RuntimeException.class, () -> reviewService.submit(1L));

        MerchantControlDTO approval = new MerchantControlDTO();
        approval.setAccountStatus("ENABLED"); approval.setBusinessStatus("SUSPENDED"); approval.setFulfillmentStatus("ENABLED");
        approval.setWithdrawalStatus("FROZEN"); approval.setSettlementStatus("FROZEN"); approval.setDepositStatus("NORMAL");
        approval.setAuditStatus("APPROVED"); approval.setExitStatus("NORMAL"); approval.setReason("资料核验通过");
        AdminContext.set(admin(7022L, "platform_reviewer", "平台认证员", "admin:read,system:manage", null));
        DmsMerchant approved = merchantService.updateMerchantControls(merchant.getId(), approval);
        assertEquals("APPROVED", approved.getAuditStatus());
        assertEquals("ACTIVE", approved.getBusinessStatus());
        assertEquals(1, approved.getStatus());

        AdminContext.set(admin(7021L, "onboarding_merchant", "入驻商户", "admin:read,shop:product", approved));
        assertEquals("PENDING", reviewService.submit(1L).getStatus());
    }

    private DmsMerchant merchant(String no, String name) {
        DmsMerchant value = new DmsMerchant();
        value.setMerchantNo(no);
        value.setMerchantName(name);
        return merchantService.saveMerchant(value);
    }

    private DmsAdminUser admin(Long id, String username, String nickname, String permissions, DmsMerchant merchant) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(id); admin.setUsername(username); admin.setNickname(nickname); admin.setPermissions(permissions);
        if (merchant != null) { admin.setMerchantId(merchant.getId()); admin.setMerchantName(merchant.getMerchantName()); }
        return admin;
    }

    private DmsShopProduct copy(DmsShopProduct value) {
        DmsShopProduct copy = new DmsShopProduct();
        BeanUtils.copyProperties(value, copy);
        return copy;
    }

    private List<MerchantProductReviewCheckDTO> reviewChecks(String rejectedCode) {
        return List.of("BASIC_INFO", "CATEGORY_QUALIFICATION", "CONTENT_COMPLIANCE", "PRICE_SETTLEMENT",
                        "STOCK_DELIVERY", "AFTER_SALE_PROMISE").stream().map(code -> {
            MerchantProductReviewCheckDTO item = new MerchantProductReviewCheckDTO();
            item.setCode(code);
            item.setPassed(!code.equals(rejectedCode));
            return item;
        }).toList();
    }
}
