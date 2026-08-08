package com.macro.mall.distribution.service;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsAgentChangeLogDao;
import com.macro.mall.distribution.dao.DmsOrderPerformanceDetailDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dao.DmsShopSkuDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.FreightTemplateRuleDTO;
import com.macro.mall.distribution.dto.FreightTemplateSaveDTO;
import com.macro.mall.distribution.dto.ProductPublishDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopSkuDTO;
import com.macro.mall.distribution.dto.ShopAddressDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.entity.DmsFreightTemplate;
import com.macro.mall.distribution.entity.DmsOrderPerformanceDetail;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopAddress;
import com.macro.mall.distribution.entity.DmsShopCategory;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsShopSku;
import com.macro.mall.distribution.vo.FreightQuoteVO;
import com.macro.mall.distribution.vo.AdminDashboardVO;
import com.macro.mall.distribution.vo.AdminMemberVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import com.macro.mall.distribution.vo.ShopProfileVO;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** 运费、商品发布及奖金基数回归测试。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisConfig.class,
        ScheduleTask.class
}))
class ShopFreightServiceTest {

    @Autowired private ShopService shopService;
    @Autowired private ShopAuthService shopAuthService;
    @Autowired private AgentService agentService;
    @Autowired private AdminDashboardService adminDashboardService;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private DmsShopSkuDao skuDao;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ShopAddressService shopAddressService;
    @Autowired private DmsCommissionRecordDao commissionRecordDao;
    @Autowired private DmsAgentChangeLogDao agentChangeLogDao;
    @Autowired private DmsOrderPerformanceDetailDao performanceDetailDao;
    @MockBean private SmsVerificationService smsVerificationService;

    @Test
    void fixedFreightIsAddedToPaymentButExcludedFromBonusAndPerformance() {
        DmsShopProduct product = useShippingProduct(1, new BigDecimal("12.00"), null, null);
        DmsShopMember inviter = createMember("13999110001", "运费直推人", null);
        submitAndPay(inviter, 1);
        DmsShopMember buyer = createMember("13999110002", "运费下级", inviter.getUserId());

        ShopOrderVO paid = submitAndPay(buyer, 1);

        assertMoney("299.00", paid.getOrder().getTotalAmount());
        assertMoney("12.00", paid.getOrder().getFreightAmount());
        assertMoney("311.00", paid.getOrder().getPayAmount());
        List<DmsCommissionRecord> commissions = commissionRecordDao.selectByOrderId(paid.getOrder().getId());
        assertEquals(1, commissions.size());
        assertMoney("74.75", commissions.get(0).getCommissionAmount());
        List<DmsOrderPerformanceDetail> performance = performanceDetailDao.selectByOrderId(paid.getOrder().getId());
        assertMoney(product.getSalePrice().toPlainString(), performance.get(0).getOrderAmount());
        assertMoney("299.00", performance.get(0).getPerformanceAmount());
    }

    @Test
    void regionalTemplateSupportsFreeFixedAndUnavailableAreas() {
        FreightTemplateSaveDTO templateDTO = new FreightTemplateSaveDTO();
        templateDTO.setTemplateName("地区运费回归模板");
        templateDTO.setDefaultMode("FREE");
        templateDTO.setStatus(1);
        FreightTemplateRuleDTO hunan = rule(List.of(List.of("湖南省")), "FIXED", "15.00");
        FreightTemplateRuleDTO changsha = rule(List.of(List.of("湖南省", "长沙市")), "UNAVAILABLE", null);
        templateDTO.setRules(List.of(hunan, changsha));
        DmsFreightTemplate template = shopService.saveFreightTemplate(templateDTO);
        useShippingProduct(3, BigDecimal.ZERO, null, template.getId());

        FreightQuoteVO free = shopService.quoteFreight(quote("海南省", "海口市", "龙华区"), null);
        assertMoney("0.00", free.getFreightAmount());
        assertMoney("299.00", free.getPayAmount());

        FreightQuoteVO extra = shopService.quoteFreight(quote("湖南省", "株洲市", "天元区"), null);
        assertMoney("15.00", extra.getFreightAmount());
        assertMoney("314.00", extra.getPayAmount());

        ApiException error = assertThrows(ApiException.class,
                () -> shopService.quoteFreight(quote("湖南省", "长沙市", "岳麓区"), null));
        assertTrue(error.getMessage().contains("暂不发货"));
    }

    @Test
    void memberCanChangeLoginPasswordOnlyWithCurrentPasswordAndSms() {
        AdminMemberCreateDTO create = new AdminMemberCreateDTO();
        create.setPhone("13999110105");
        create.setUsername("member_13999110105");
        create.setNickname("密码修改会员");
        create.setPassword("login123");
        DmsShopMember member = shopAuthService.createAdminMember(create);

        ShopPasswordChangeDTO wrong = new ShopPasswordChangeDTO();
        wrong.setCurrentPassword("wrong123");
        wrong.setNewPassword("newpass123");
        assertThrows(ApiException.class, () -> shopAuthService.changePassword(member, wrong));

        ShopPasswordChangeDTO missingSms = new ShopPasswordChangeDTO();
        missingSms.setCurrentPassword("login123");
        missingSms.setNewPassword("newpass123");
        doThrow(new ApiException("请输入6位短信验证码")).when(smsVerificationService)
                .verifyAndConsume(member.getPhone(), null, 8);
        assertThrows(ApiException.class, () -> shopAuthService.changePassword(member, missingSms));
        assertTrue(BCrypt.checkpw("login123", memberDao.selectById(member.getId()).getPasswordHash()));

        ShopPasswordChangeDTO correct = new ShopPasswordChangeDTO();
        correct.setCurrentPassword("login123");
        correct.setNewPassword("newpass123");
        correct.setSmsCode("123456");
        assertTrue(shopAuthService.changePassword(member, correct));
        verify(smsVerificationService).verifyAndConsume(member.getPhone(), "123456", 8);
        assertTrue(BCrypt.checkpw("newpass123", memberDao.selectById(member.getId()).getPasswordHash()));
        assertFalse(BCrypt.checkpw("login123", memberDao.selectById(member.getId()).getPasswordHash()));
    }

    @Test
    void deletingDefaultAddressPromotesAnotherSavedAddress() {
        DmsShopMember member = createMember("13999110106", "默认地址会员", null);
        DmsShopAddress first = shopAddressService.save(member, address("长沙市岳麓区一号", 1));
        DmsShopAddress second = shopAddressService.save(member, address("长沙市岳麓区二号", 0));
        assertEquals(1, first.getIsDefault());

        assertTrue(shopAddressService.delete(member, first.getId()));
        List<DmsShopAddress> remaining = shopAddressService.list(member);
        assertEquals(1, remaining.size());
        assertEquals(second.getId(), remaining.get(0).getId());
        assertEquals(1, remaining.get(0).getIsDefault());
    }

    @Test
    void aggregatePublishAcceptsBlankSkuAttributesAndSavesCompleteProduct() {
        DmsShopProduct product = new DmsShopProduct();
        product.setProductName("事务发布回归商品");
        product.setCategoryName("测试分类");
        product.setSalePrice(new BigDecimal("88.00"));
        product.setMarketPrice(new BigDecimal("99.00"));
        product.setCostAmount(new BigDecimal("30.00"));
        product.setStock(20);
        product.setStatus(1);
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");
        product.setFreightType(0);

        ShopSkuDTO sku = new ShopSkuDTO();
        sku.setSkuName("默认规格");
        sku.setAttrsJson("   ");
        sku.setSalePrice(new BigDecimal("88.00"));
        sku.setMarketPrice(new BigDecimal("99.00"));
        sku.setCostAmount(new BigDecimal("30.00"));
        sku.setStock(20);
        sku.setStatus(1);
        ProductPublishDTO publish = new ProductPublishDTO();
        publish.setProduct(product);
        publish.setSkus(List.of(sku));

        DmsShopProduct saved = shopService.publishProduct(null, publish);

        assertNotNull(saved.getId());
        assertEquals("湖南省 长沙市 岳麓区", saved.getDeliveryAddress());
        List<DmsShopSku> skus = skuDao.selectByProductId(saved.getId(), null);
        assertEquals(1, skus.size());
        assertEquals("{}", skus.get(0).getAttrsJson());
        assertNotNull(productDao.selectById(saved.getId()));
    }

    @Test
    void productAndSkuPvCannotExceedTheirSalePrice() {
        DmsShopProduct product = productDao.selectById(1L);
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");
        product.setPvValue(product.getSalePrice().add(BigDecimal.ONE));
        ApiException productError = assertThrows(ApiException.class,
                () -> shopService.updateProduct(product.getId(), product));
        assertTrue(productError.getMessage().contains("商品PV不能超过销售价"));

        DmsShopSku existing = skuDao.selectById(1L);
        ShopSkuDTO sku = new ShopSkuDTO();
        sku.setProductId(existing.getProductId());
        sku.setSkuName(existing.getSkuName());
        sku.setSkuNo(existing.getSkuNo());
        sku.setAttrsJson(existing.getAttrsJson());
        sku.setSalePrice(existing.getSalePrice());
        sku.setMarketPrice(existing.getMarketPrice());
        sku.setCostAmount(existing.getCostAmount());
        sku.setPvValue(existing.getSalePrice().add(BigDecimal.ONE));
        sku.setStock(existing.getStock());
        sku.setStatus(1);
        ApiException skuError = assertThrows(ApiException.class, () -> shopService.updateSku(existing.getId(), sku));
        assertTrue(skuError.getMessage().contains("SKU PV不能超过销售价"));
    }

    @Test
    void disablingProductPvIgnoresHiddenHistoricalPvValues() {
        jdbcTemplate.update("UPDATE dms_tenant_display_config SET show_pv=0 WHERE tenant_id=1");

        DmsShopProduct product = productDao.selectById(1L);
        product.setSalePrice(new BigDecimal("0.01"));
        product.setCostAmount(new BigDecimal("0.01"));
        product.setPvValue(new BigDecimal("100.00"));
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");

        DmsShopProduct saved = shopService.updateProduct(product.getId(), product);
        assertMoney("0.00", saved.getPvValue());

        DmsShopSku sku = skuDao.selectById(1L);
        ShopSkuDTO skuDTO = new ShopSkuDTO();
        skuDTO.setProductId(sku.getProductId());
        skuDTO.setSkuName(sku.getSkuName());
        skuDTO.setSkuNo(sku.getSkuNo());
        skuDTO.setAttrsJson(sku.getAttrsJson());
        skuDTO.setSalePrice(new BigDecimal("0.01"));
        skuDTO.setCostAmount(new BigDecimal("0.01"));
        skuDTO.setPvValue(new BigDecimal("100.00"));
        skuDTO.setStock(sku.getStock());
        skuDTO.setStatus(1);

        DmsShopSku savedSku = shopService.updateSku(sku.getId(), skuDTO);
        assertMoney("0.00", savedSku.getPvValue());
    }

    @Test
    void disablingProductPvAlsoAllowsAggregatePublishWithHiddenHistoricalPvValues() {
        jdbcTemplate.update("UPDATE dms_tenant_display_config SET show_pv=0 WHERE tenant_id=1");

        DmsShopProduct product = productDao.selectById(1L);
        product.setSalePrice(new BigDecimal("10.00"));
        product.setCostAmount(new BigDecimal("1.00"));
        product.setPvValue(new BigDecimal("100.00"));
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");

        DmsShopSku existingSku = skuDao.selectById(1L);
        ShopSkuDTO sku = new ShopSkuDTO();
        sku.setId(existingSku.getId());
        sku.setProductId(existingSku.getProductId());
        sku.setSkuName(existingSku.getSkuName());
        sku.setSkuNo(existingSku.getSkuNo());
        sku.setAttrsJson(existingSku.getAttrsJson());
        sku.setSalePrice(new BigDecimal("10.00"));
        sku.setMarketPrice(existingSku.getMarketPrice());
        sku.setCostAmount(new BigDecimal("1.00"));
        sku.setPvValue(new BigDecimal("100.00"));
        sku.setStock(existingSku.getStock());
        sku.setStatus(1);

        ProductPublishDTO publish = new ProductPublishDTO();
        publish.setProduct(product);
        publish.setSkus(List.of(sku));
        publish.setRemovedSkuIds(List.of());

        DmsShopProduct saved = shopService.publishProduct(product.getId(), publish);

        assertMoney("10.00", saved.getSalePrice());
        assertMoney("0.00", saved.getPvValue());
        assertMoney("0.00", skuDao.selectById(existingSku.getId()).getPvValue());
    }

    @Test
    void disabledProductPvIsZeroInPublicProductSnapshots() {
        jdbcTemplate.update("UPDATE dms_tenant_display_config SET show_pv=0 WHERE tenant_id=1");

        ShopProductDetailVO detail = shopService.getProductDetail(1L);
        assertMoney("0.00", detail.getProduct().getPvValue());
        detail.getSkus().forEach(sku -> assertMoney("0.00", sku.getPvValue()));

        shopService.getHome(null).getFeaturedProducts()
                .forEach(product -> assertMoney("0.00", product.getPvValue()));
    }

    @Test
    void legacyOversizedPvIsCappedAndQuantityIsMultipliedInOrderSnapshot() {
        jdbcTemplate.update("UPDATE dms_shop_product SET sale_price=99.00, pv_value=220.00 WHERE id=1");
        jdbcTemplate.update("UPDATE dms_shop_sku SET sale_price=99.00, pv_value=0.00 WHERE id=1");

        ShopProductDetailVO detail = shopService.getProductDetail(1L);
        assertMoney("99.00", detail.getProduct().getPvValue());
        assertMoney("99.00", detail.getSkus().get(0).getPvValue());

        ShopOrderVO order = shopService.submitOrder(pendingOrder(2, 1L));
        assertMoney("99.00", order.getItems().get(0).getPvValue());
        assertMoney("198.00", order.getItems().get(0).getTotalPv());
        assertMoney("198.00", order.getOrder().getTotalPv());
    }

    @Test
    void skuPvOverridesProductDefaultAndMissingSkuIsRejected() {
        jdbcTemplate.update("UPDATE dms_shop_product SET sale_price=80.00, pv_value=50.00 WHERE id=1");
        jdbcTemplate.update("UPDATE dms_shop_sku SET sale_price=80.00, pv_value=60.00 WHERE id=1");

        ShopOrderVO order = shopService.submitOrder(pendingOrder(2, 1L));
        assertMoney("60.00", order.getItems().get(0).getPvValue());
        assertMoney("120.00", order.getItems().get(0).getTotalPv());

        ApiException error = assertThrows(ApiException.class, () -> shopService.submitOrder(pendingOrder(1, null)));
        assertTrue(error.getMessage().contains("请先选择具体规格"));
    }

    @Test
    void expiredPendingOrderClosesOnceAndRestoresInventory() {
        DmsShopMember member = createMember("13999110107", "超时订单会员", null);
        DmsShopProduct before = productDao.selectById(1L);
        int originalStock = before.getStock();
        ShopOrderSubmitDTO dto = quote("湖南省", "长沙市", "岳麓区");
        dto.setReceiverName(member.getNickname());
        dto.setReceiverPhone(member.getPhone());
        dto.setReceiverDetailAddress("测试路1号");
        dto.setPayType("ALIPAY");
        ShopOrderVO pending = shopService.submitOrder(dto, member);
        assertEquals(originalStock - 1, productDao.selectById(1L).getStock());

        jdbcTemplate.update("UPDATE dms_shop_order SET create_time=DATEADD('MINUTE', -31, CURRENT_TIMESTAMP) WHERE id=?",
                pending.getOrder().getId());
        assertEquals(1, shopService.closeExpiredPendingOrders(20));
        assertEquals(4, orderDao.selectById(pending.getOrder().getId()).getStatus());
        assertEquals(originalStock, productDao.selectById(1L).getStock());

        assertEquals(0, shopService.closeExpiredPendingOrders(20));
        assertEquals(originalStock, productDao.selectById(1L).getStock());
    }

    @Test
    void purchaseLimitCountsPendingOrdersAndRejectsAdditionalQuantity() {
        DmsShopProduct product = useShippingProduct(0, BigDecimal.ZERO, null, null);
        product.setPurchaseLimit(2);
        shopService.updateProduct(product.getId(), product);
        DmsShopMember member = createMember("13999110108", "限购测试会员", null);

        shopService.submitOrder(pendingOrder(2, 1L), member);

        ApiException error = assertThrows(ApiException.class,
                () -> shopService.submitOrder(pendingOrder(1, 1L), member));
        assertTrue(error.getMessage().contains("每位会员限购 2 件"));
    }

    @Test
    void profileUsesAggregateOrderCountsAndOrderListSupportsStatusFiltering() {
        DmsShopMember member = createMember("13999110109", "个人中心性能会员", null);
        shopService.submitOrder(pendingOrder(1, 1L), member);

        ShopProfileVO profile = shopService.getProfile(member, null);

        assertNotNull(profile.getOrderSummary());
        assertEquals(1L, profile.getOrderSummary().getTotal());
        assertEquals(1L, profile.getOrderSummary().getPendingPayment());
        assertTrue(profile.getOrders() == null || profile.getOrders().isEmpty());
        assertEquals(1, shopService.listOrders(member.getUserId(), null, "PENDING_PAYMENT").size());
        assertTrue(shopService.listOrders(member.getUserId(), null, "PENDING_SHIPMENT").isEmpty());
    }

    @Test
    void categoryCanBeCreatedAndRenameCascadesToExistingProducts() {
        DmsShopCategory category = new DmsShopCategory();
        category.setCategoryName("分类新增回归");
        category.setSort(20);
        category.setStatus(1);
        DmsShopCategory saved = shopService.saveCategory(category);
        assertNotNull(saved.getId());

        DmsShopProduct product = productDao.selectById(1L);
        product.setCategoryName(saved.getCategoryName());
        productDao.update(product);

        DmsShopCategory renamed = new DmsShopCategory();
        renamed.setCategoryName("分类改名回归");
        renamed.setSort(30);
        renamed.setStatus(1);
        shopService.updateCategory(saved.getId(), renamed);

        assertEquals("分类改名回归", productDao.selectById(1L).getCategoryName());
        DmsShopCategory duplicated = new DmsShopCategory();
        duplicated.setCategoryName("分类改名回归");
        assertThrows(ApiException.class, () -> shopService.saveCategory(duplicated));
    }

    @Test
    void categoryDeletionOnlyAllowsUnusedCategories() {
        DmsShopCategory unused = new DmsShopCategory();
        unused.setCategoryName("待删除空分类");
        DmsShopCategory savedUnused = shopService.saveCategory(unused);

        assertTrue(shopService.deleteCategory(savedUnused.getId()));
        assertTrue(shopService.listAdminCategories(1L, null).stream()
                .noneMatch(category -> savedUnused.getId().equals(category.getId())));

        DmsShopCategory inUse = new DmsShopCategory();
        inUse.setCategoryName("仍有商品分类");
        DmsShopCategory savedInUse = shopService.saveCategory(inUse);
        DmsShopProduct product = productDao.selectById(1L);
        product.setCategoryName(savedInUse.getCategoryName());
        productDao.update(product);

        ApiException error = assertThrows(ApiException.class, () -> shopService.deleteCategory(savedInUse.getId()));
        assertTrue(error.getMessage().contains("还有1个商品"));
        assertTrue(shopService.listAdminCategories(1L, null).stream()
                .anyMatch(category -> savedInUse.getId().equals(category.getId())));
    }

    @Test
    void adminMemberListExposesLoginAccountInviterStatusLevelAndAssets() {
        DmsShopMember inviter = createMember("13999110101", "列表邀请人", null);
        DmsShopMember member = createMember("13999110102", "列表会员", inviter.getUserId());

        List<AdminMemberVO> rows = shopAuthService.listAdminMembers(member.getPhone(), 1, null, null);

        assertEquals(1, rows.size());
        AdminMemberVO row = rows.get(0);
        assertEquals(member.getUsername(), row.getMemberAccount());
        assertEquals(inviter.getUserId(), row.getInviterUserId());
        assertEquals(inviter.getUsername(), row.getInviterMemberAccount());
        assertEquals("列表邀请人", row.getInviterName());
        assertFalse(row.getLoginLocked());
        assertFalse(row.getPromotionActivated());
        assertMoney("0.00", row.getAvailableBalance());
        assertMoney("0.00", row.getUnsettledCommission());
        assertMoney("0.00", row.getTeamPerformance());
        assertEquals(0, row.getTotalOrders());
    }

    @Test
    void unifiedMemberLevelActionActivatesThenSupportsDirectDowngrade() {
        DmsShopMember member = createMember("13999110104", "统一调级会员", null);

        AgentInfoVO activated = shopAuthService.adjustMemberLevel(member.getId(), 6, "后台直接设为二星董事");
        assertEquals(6, activated.getAgentLevel());
        List<AdminMemberVO> activeRows = shopAuthService.listAdminMembers(member.getPhone(), 1, 1, 6);
        assertEquals(1, activeRows.size());
        assertTrue(activeRows.get(0).getPromotionActivated());
        assertEquals(6, activeRows.get(0).getAgentLevel());
        assertTrue(agentService.getRootAgents().stream().anyMatch(item -> item.getId().equals(activated.getId())));
        assertFalse(agentChangeLogDao.selectByAgentId(activated.getId()).isEmpty());

        AgentInfoVO downgraded = shopAuthService.adjustMemberLevel(member.getId(), 3, "后台直接调整为店铺");
        assertEquals(3, downgraded.getAgentLevel());
        assertEquals(3, agentChangeLogDao.selectByAgentId(activated.getId()).get(0).getNewLevel());
    }

    @Test
    void adminDashboardReturnsRealAggregatesAndCompleteSeries() {
        DmsShopMember member = createMember("13999110103", "控制台会员", null);
        ShopAddressDTO savedAddress = address("工作台地区统计地址", 1);
        savedAddress.setProvince("广东省");
        savedAddress.setCity("深圳市");
        savedAddress.setDistrict("南山区");
        shopAddressService.save(member, savedAddress);
        submitAndPay(member, 1);
        jdbcTemplate.update("""
                INSERT INTO dms_withdraw_record
                  (withdraw_no, agent_id, user_id, withdraw_amount, withdraw_type, account_name, status, pay_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "WD-DASHBOARD-PAID", 1L, member.getUserId(), new BigDecimal("88.00"), 1,
                member.getNickname(), 3);
        jdbcTemplate.update("""
                INSERT INTO dms_withdraw_record
                  (withdraw_no, agent_id, user_id, withdraw_amount, withdraw_type, account_name, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "WD-DASHBOARD-PENDING", 1L, member.getUserId(), new BigDecimal("12.00"), 1,
                member.getNickname(), 0);

        AdminDashboardVO dashboard = adminDashboardService.getDashboard();

        assertTrue(dashboard.getMemberCount() >= 1);
        assertTrue(dashboard.getPromotionMemberCount() >= 1);
        assertEquals(dashboard.getMemberCount(), dashboard.getRegisteredMemberCount());
        assertEquals(dashboard.getPromotionMemberCount(), dashboard.getValidMemberCount());
        assertTrue(dashboard.getMonthNewMemberCount() >= 1);
        assertTrue(dashboard.getTotalSalesAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getMonthSalesAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getLast7DaysSalesAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getTodaySalesAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getTodayPerformance().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getMemberRegionDistribution().stream()
                .anyMatch(row -> "湖南省".equals(row.getRegionName()) && row.getMemberCount() >= 1));
        assertTrue(dashboard.getMemberRegionDistribution().stream()
                .noneMatch(row -> "广东省".equals(row.getRegionName())));
        assertMoney("12.00", dashboard.getPendingWithdrawAmount());
        assertMoney("88.00", dashboard.getTotalWithdrawAmount());
        assertMoney("88.00", dashboard.getMonthWithdrawAmount());
        assertTrue(dashboard.getTotalReceiptAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getTotalPayoutAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getTotalProfitAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dashboard.getProfitRate().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(dashboard.getProductRanking().isEmpty());
        assertEquals("轻奢焕活礼盒", dashboard.getProductRanking().get(0).getProductName());
        assertEquals(1, dashboard.getProductRanking().get(0).getRanking());
        assertEquals(1L, dashboard.getAddressedMemberCount());
        assertEquals("湖南省", dashboard.getMemberRegionDistribution().get(0).getRegionName());
        assertEquals(1L, dashboard.getMemberRegionDistribution().get(0).getMemberCount());
        assertMoney("100.00", dashboard.getMemberRegionDistribution().get(0).getPercentage());
        assertEquals(30, dashboard.getPerformanceTrend().size());
        assertEquals(8, dashboard.getLevelDistribution().size());
        assertNotNull(dashboard.getPendingWithdraws());
        assertNotNull(dashboard.getLatestCommissions());
    }

    @Test
    void adminDashboardIncludesTwelveMonthSalesTrendByPaidMonth() {
        DmsShopMember member = createMember("13999110107", "月度趋势会员", null);
        ShopOrderVO paid = submitAndPay(member, 1);
        jdbcTemplate.update("UPDATE dms_shop_order SET pay_time=? WHERE id=?",
                LocalDateTime.now().minusMonths(1), paid.getOrder().getId());

        AdminDashboardVO dashboard = adminDashboardService.getDashboard();

        assertEquals(12, dashboard.getMonthlyPerformanceTrend().size());
        LocalDate previousMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        assertTrue(dashboard.getMonthlyPerformanceTrend().stream()
                .anyMatch(row -> previousMonth.equals(row.getStatDate())
                        && row.getPerformanceAmount().compareTo(BigDecimal.ZERO) > 0));
    }

    private DmsShopProduct useShippingProduct(int freightType, BigDecimal freightAmount,
                                               BigDecimal freeShippingAmount, Long templateId) {
        DmsShopProduct product = productDao.selectById(1L);
        product.setDeliveryProvince("湖南省");
        product.setDeliveryCity("长沙市");
        product.setDeliveryDistrict("岳麓区");
        product.setFreightType(freightType);
        product.setFreightAmount(freightAmount);
        product.setFreeShippingAmount(freeShippingAmount);
        product.setFreightTemplateId(templateId);
        return shopService.updateProduct(product.getId(), product);
    }

    private FreightTemplateRuleDTO rule(List<List<String>> paths, String mode, String amount) {
        FreightTemplateRuleDTO rule = new FreightTemplateRuleDTO();
        rule.setRegionPaths(paths);
        rule.setMode(mode);
        rule.setFreightAmount(amount == null ? BigDecimal.ZERO : new BigDecimal(amount));
        return rule;
    }

    private ShopOrderSubmitDTO quote(String province, String city, String district) {
        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setReceiverProvince(province);
        dto.setReceiverCity(city);
        dto.setReceiverDistrict(district);
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        dto.setItems(List.of(item));
        return dto;
    }

    private DmsShopMember createMember(String phone, String nickname, Long inviterUserId) {
        AdminMemberCreateDTO create = new AdminMemberCreateDTO();
        create.setPhone(phone);
        create.setUsername("member_" + phone);
        create.setNickname(nickname);
        create.setInviterUserId(inviterUserId);
        create.setActivateDistribution(false);
        return shopAuthService.createAdminMember(create);
    }

    private ShopAddressDTO address(String detail, int isDefault) {
        ShopAddressDTO dto = new ShopAddressDTO();
        dto.setReceiverName("测试收货人");
        dto.setReceiverPhone("13999110106");
        dto.setProvince("湖南省");
        dto.setCity("长沙市");
        dto.setDistrict("岳麓区");
        dto.setDetailAddress(detail);
        dto.setIsDefault(isDefault);
        return dto;
    }

    private ShopOrderVO submitAndPay(DmsShopMember member, int quantity) {
        ShopOrderSubmitDTO dto = quote("湖南省", "株洲市", "天元区");
        dto.setReceiverName(member.getNickname());
        dto.setReceiverPhone(member.getPhone());
        dto.setReceiverDetailAddress("嵩山路1号");
        dto.setPayType("ALIPAY");
        dto.getItems().get(0).setQuantity(quantity);
        return shopService.markOrderPaid(shopService.submitOrder(dto, member).getOrder().getId(), "ALIPAY");
    }

    private ShopOrderSubmitDTO pendingOrder(int quantity, Long skuId) {
        ShopOrderSubmitDTO dto = quote("湖南省", "株洲市", "天元区");
        dto.setReceiverName("PV测试收货人");
        dto.setReceiverPhone("13999110999");
        dto.setReceiverDetailAddress("测试路1号");
        dto.setPayType("ALIPAY");
        dto.getItems().get(0).setSkuId(skuId);
        dto.getItems().get(0).setQuantity(quantity);
        return dto;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
