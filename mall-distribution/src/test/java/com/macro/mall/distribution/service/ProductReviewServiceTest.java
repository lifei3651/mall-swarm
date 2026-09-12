package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.ProductReviewReplyDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.security.AdminContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.dao.DmsShopProductReviewDao;
import com.macro.mall.distribution.dto.ProductReviewStatusDTO;
import com.macro.mall.distribution.dto.ProductReviewSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopProductReview;
import com.macro.mall.distribution.vo.ProductReviewPageVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class ProductReviewServiceTest {

    @Autowired private ProductReviewService productReviewService;
    @Autowired private DmsShopProductReviewDao productReviewDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void loginPlatform() {
        TenantContext.setTenantId(1L);
        loginAdmin(null);
    }

    @AfterEach
    void clearContexts() {
        AdminContext.clear();
        TenantContext.clear();
    }

    private void loginAdmin(Long merchantId) {
        DmsAdminUser admin = new DmsAdminUser();
        admin.setId(9900L);
        admin.setNickname("系统管理员");
        admin.setStatus(1);
        admin.setMerchantId(merchantId);
        AdminContext.set(admin);
    }

    private ProductReviewReplyDTO reply(String content, Integer expected) {
        ProductReviewReplyDTO dto = new ProductReviewReplyDTO();
        dto.setContent(content);
        dto.setExpectedVersion(expected);
        return dto;
    }

    private DmsShopProductReview merchantReview(Long orderId, Long merchantId) {
        DmsShopMember member = member(orderId, "真实买家", "13900001999");
        insertOrderItem(orderId, "REPLY-" + orderId, member.getUserId(), 3, 1L);
        jdbcTemplate.update("UPDATE dms_shop_order SET merchant_id=? WHERE id=?", merchantId, orderId);
        return productReviewService.submitReview(1L, member, review(4, "商品使用体验评价"));
    }

    @Test
    void merchantAndPlatformRepliesAreIndependentPublicAndAudited() throws Exception {
        DmsShopProductReview review = merchantReview(99501L, 81L);
        loginAdmin(81L);
        assertTrue(productReviewService.replyReview(review.getId(), reply("  感谢反馈，我们会继续改进。  ", null)));
        loginAdmin(null);
        assertTrue(productReviewService.replyReview(review.getId(), reply("平台已关注您的反馈。", null)));
        assertTrue(productReviewService.replyReview(review.getId(), reply("平台会持续跟进。", 1)));
        DmsShopProductReview saved = productReviewDao.selectById(review.getId());
        assertEquals("感谢反馈，我们会继续改进。", saved.getMerchantReply());
        assertEquals("平台会持续跟进。", saved.getPlatformReply());
        assertNotNull(saved.getMerchantReplyTime());
        assertNotNull(saved.getPlatformReplyTime());
        assertEquals(4, saved.getRating());
        assertEquals("商品使用体验评价", saved.getContent());
        var visible = productReviewService.listProductReviews(1L, null, 1, 10).getPage().getList().get(0);
        assertEquals(saved.getMerchantReply(), visible.getMerchantReply());
        assertEquals(saved.getPlatformReply(), visible.getPlatformReply());
        String json = objectMapper.writeValueAsString(visible);
        assertFalse(json.contains("ReplyBy"));
        assertFalse(json.contains("orderNo"));
        assertFalse(json.contains("userId"));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dms_operation_log WHERE module_name='PRODUCT_REVIEW' AND target_id=?", Integer.class, String.valueOf(review.getId())));
    }

    @Test
    void merchantCannotReadOrReplyToOtherMerchantsAndCannotHideReviews() {
        DmsShopProductReview own = merchantReview(99502L, 81L);
        DmsShopProductReview other = merchantReview(99503L, 82L);
        loginAdmin(81L);
        var list = productReviewService.listAdminReviews(null, null, null, null, 1, 10);
        assertEquals(1L, list.getTotal());
        assertEquals(own.getId(), list.getList().get(0).getId());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(other.getId(), reply("越权回复", null)));
        ProductReviewStatusDTO hide = new ProductReviewStatusDTO();
        hide.setStatus(0); hide.setReason("不允许商家隐藏");
        assertThrows(ApiException.class, () -> productReviewService.updateReviewStatus(own.getId(), hide));
        assertEquals(1, productReviewDao.selectById(own.getId()).getStatus());
    }

    @Test
    void orderMerchantSnapshotControlsAccessEvenAfterProductReassignment() {
        DmsShopProductReview review = merchantReview(99504L, 81L);
        jdbcTemplate.update("UPDATE dms_shop_product SET merchant_id=82 WHERE id=1");
        loginAdmin(82L);
        assertTrue(productReviewService.listAdminReviews(null, null, null, null, 1, 10).getList().isEmpty());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("不能夺取历史评价", null)));
        loginAdmin(81L);
        assertTrue(productReviewService.replyReview(review.getId(), reply("仍由原成交商家负责", null)));
    }

    @Test
    void bothActorsAreTenantScopedAndHiddenRepliesDisappearWithReview() {
        DmsShopProductReview review = merchantReview(99505L, 81L);
        assertTrue(productReviewService.replyReview(review.getId(), reply("平台回复", null)));
        TenantContext.setTenantId(2L);
        assertTrue(productReviewService.listAdminReviews(null, null, null, null, 1, 10).getList().isEmpty());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("跨商城回复", null)));
        loginAdmin(81L);
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("跨商城商家回复", null)));
        TenantContext.setTenantId(1L); loginAdmin(null);
        ProductReviewStatusDTO hide = new ProductReviewStatusDTO(); hide.setStatus(0); hide.setReason("回归检查");
        productReviewService.updateReviewStatus(review.getId(), hide);
        assertTrue(productReviewService.listProductReviews(1L, null, 1, 10).getPage().getList().isEmpty());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("已隐藏不能回复", 1)));
        assertEquals("平台回复", productReviewDao.selectById(review.getId()).getPlatformReply());
        hide.setStatus(1); productReviewService.updateReviewStatus(review.getId(), hide);
        assertEquals("平台回复", productReviewService.listProductReviews(1L, null, 1, 10).getPage().getList().get(0).getPlatformReply());
    }

    @Test
    void repliesRejectBlankOversizedAnonymousDisabledAndStaleEdits() {
        DmsShopProductReview review = merchantReview(99506L, 81L);
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("  ", null)));
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("字".repeat(501), null)));
        AdminContext.clear();
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("未登录", null)));
        loginAdmin(null); AdminContext.get().setStatus(0);
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("已停用", null)));
        loginAdmin(null);
        assertTrue(productReviewService.replyReview(review.getId(), reply("第一条回复", null)));
        var before = productReviewDao.selectById(review.getId()).getPlatformReplyTime();
        assertTrue(productReviewService.replyReview(review.getId(), reply("第一条回复", null)));
        assertEquals(before, productReviewDao.selectById(review.getId()).getPlatformReplyTime());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("不能用旧版本覆盖", null)));
        assertTrue(productReviewService.replyReview(review.getId(), reply("更新回复", 1)));
        assertEquals("更新回复", productReviewDao.selectById(review.getId()).getPlatformReply());
        assertEquals(2, productReviewDao.selectById(review.getId()).getPlatformReplyVersion());
        assertThrows(ApiException.class, () -> productReviewService.replyReview(review.getId(), reply("同一旧版本不能再次覆盖", 1)));
    }

    @Test
    void receivedOrderCanReviewAndRepeatPurchaseCanReviewAgain() {
        DmsShopMember member = member(991001L, "评价会员甲", "13900001001");
        insertOrderItem(99101L, "REVIEW-99101", member.getUserId(), 3, 1L);
        insertOrderItem(99102L, "REVIEW-99102", member.getUserId(), 3, 1L);
        assertEquals(1, productReviewDao.countUnreviewedByOrderId(member.getUserId(), 99101L, 1L));

        DmsShopProductReview first = productReviewService.submitReview(1L, member, review(5, "商品很好，物流也很快"));
        assertNotNull(first.getId());
        assertEquals("评***甲", first.getReviewerName());
        assertEquals(0, productReviewDao.countUnreviewedByOrderId(member.getUserId(), first.getOrderId(), 1L));

        ProductReviewPageVO afterFirst = productReviewService.listProductReviews(1L, member, 1, 10);
        assertEquals(1L, afterFirst.getReviewCount());
        assertEquals("5.0", afterFirst.getAverageRating().toPlainString());
        assertTrue(afterFirst.getCanReview());
        assertEquals(1, afterFirst.getPage().getList().size());

        productReviewService.submitReview(1L, member, review(4, "复购体验依然不错"));

        ProductReviewPageVO afterRepeat = productReviewService.listProductReviews(1L, member, 1, 10);
        assertEquals(2L, afterRepeat.getReviewCount());
        assertEquals("4.5", afterRepeat.getAverageRating().toPlainString());
        assertFalse(afterRepeat.getCanReview());
    }

    @Test
    void nonReceivedOrderCannotReview() {
        DmsShopMember member = member(991002L, "待收货会员", "13900001002");
        insertOrderItem(99103L, "REVIEW-99103", member.getUserId(), 2, 1L);

        ApiException error = assertThrows(ApiException.class,
                () -> productReviewService.submitReview(1L, member, review(5, "尚未确认收货")));
        assertTrue(error.getMessage().contains("确认收货"));
    }

    @Test
    void hiddenReviewDisappearsFromFrontButRemainsInAdminAndCanBeRestored() {
        DmsShopMember member = member(991003L, "评价会员乙", "13900001003");
        insertOrderItem(99104L, "REVIEW-99104", member.getUserId(), 3, 1L);
        DmsShopProductReview review = productReviewService.submitReview(1L, member, review(3, "这是后台显隐回归评价"));

        ProductReviewStatusDTO hide = new ProductReviewStatusDTO();
        hide.setStatus(0);
        hide.setReason("回归测试隐藏");
        assertTrue(productReviewService.updateReviewStatus(review.getId(), hide));
        assertEquals(0L, productReviewService.listProductReviews(1L, member, 1, 10).getReviewCount());

        DmsShopProductReview hidden = productReviewService.listAdminReviews(null, 1L, null, 0, 1, 10)
                .getList().get(0);
        assertEquals("回归测试隐藏", hidden.getHiddenReason());
        assertEquals("系统管理员", hidden.getHiddenByName());

        ProductReviewStatusDTO restore = new ProductReviewStatusDTO();
        restore.setStatus(1);
        assertTrue(productReviewService.updateReviewStatus(review.getId(), restore));
        assertEquals(1L, productReviewService.listProductReviews(1L, member, 1, 10).getReviewCount());
    }

    @Test
    void reviewSummaryIncludesRatingDistribution() {
        DmsShopMember member = member(991010L, "分布会员", "13900001010");
        insertOrderItem(99110L, "DIST-99110", member.getUserId(), 3, 1L);
        productReviewService.submitReview(1L, member, review(5, "五星好评"));
        insertOrderItem(99111L, "DIST-99111", member.getUserId(), 3, 1L);
        productReviewService.submitReview(1L, member, review(3, "三星中评"));

        ProductReviewPageVO result = productReviewService.listProductReviews(1L, member, 1, 10);
        assertEquals(1L, result.getStar5Count());
        assertEquals(0L, result.getStar4Count());
        assertEquals(1L, result.getStar3Count());
        assertEquals(0L, result.getStar2Count());
        assertEquals(0L, result.getStar1Count());
    }

    @Test
    void exactOrderItemReviewDoesNotConsumeAnotherRepurchaseOrder() {
        DmsShopMember member = member(991020L, "复购评价会员", "13900001020");
        insertOrderItem(99120L, "EXACT-99120", member.getUserId(), 3, 1L);
        insertOrderItem(99121L, "EXACT-99121", member.getUserId(), 3, 1L);
        Long firstItemId = orderItemId(99120L);
        Long secondItemId = orderItemId(99121L);

        ProductReviewSubmitDTO exact = review(5, "只评价指定的第一笔订单");
        exact.setOrderItemId(firstItemId);
        DmsShopProductReview submitted = productReviewService.submitReview(1L, member, exact);

        assertEquals(99120L, submitted.getOrderId());
        assertEquals(firstItemId, submitted.getOrderItemId());
        assertEquals(0, productReviewDao.countUnreviewedByOrderId(member.getUserId(), 99120L, 1L));
        assertEquals(1, productReviewDao.countUnreviewedByOrderId(member.getUserId(), 99121L, 1L));
        assertFalse(productReviewService.listProductReviews(1L, member, firstItemId, 1, 10).getCanReview());
        assertTrue(productReviewService.listProductReviews(1L, member, secondItemId, 1, 10).getCanReview());
    }

    private DmsShopMember member(Long userId, String nickname, String phone) {
        DmsShopMember member = new DmsShopMember();
        member.setUserId(userId);
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setStatus(1);
        return member;
    }

    private ProductReviewSubmitDTO review(int rating, String content) {
        ProductReviewSubmitDTO dto = new ProductReviewSubmitDTO();
        dto.setRating(rating);
        dto.setContent(content);
        return dto;
    }

    private void insertOrderItem(Long orderId, String orderNo, Long userId, int status, Long productId) {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id, order_no, tenant_id, user_id, receiver_name, receiver_phone, receiver_address,
                 total_amount, freight_amount, discount_amount, pay_amount, total_pv, total_cost, status,
                 pay_time, receive_time)
                VALUES (?, ?, 1, ?, '测试收货人', '13900000000', '湖南省长沙市岳麓区测试路1号',
                        299, 0, 0, 299, 0, 100, ?, CURRENT_TIMESTAMP,
                        CASE WHEN ? = 3 THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, orderId, orderNo, userId, status, status);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order_item
                (order_id, order_no, product_id, product_name, price, quantity, total_amount,
                 pv_value, total_pv, cost_amount, total_cost)
                VALUES (?, ?, ?, '轻奢焕活礼盒', 299, 1, 299, 0, 0, 100, 100)
                """, orderId, orderNo, productId);
    }

    private Long orderItemId(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM dms_shop_order_item WHERE order_id=? ORDER BY id LIMIT 1", Long.class, orderId);
    }
}
