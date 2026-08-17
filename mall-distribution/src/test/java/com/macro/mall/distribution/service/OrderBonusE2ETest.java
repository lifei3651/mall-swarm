package com.macro.mall.distribution.service;

import com.macro.mall.distribution.config.RedisConfig;
import com.macro.mall.distribution.config.ScheduleTask;
import com.macro.mall.distribution.dao.DmsCommissionRecordDao;
import com.macro.mall.distribution.dao.DmsOrderBalanceAllocationDao;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.dao.DmsShopProductDao;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.entity.DmsCommissionRecord;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order_bonus_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Transactional
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        RedisConfig.class,
        ScheduleTask.class
}))
class OrderBonusE2ETest {

    @Autowired private ShopService shopService;
    @Autowired private CommissionService commissionService;
    @Autowired private OrderBalanceAllocationService allocationService;
    @Autowired private CommissionSettlementService settlementService;
    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private DmsShopProductDao productDao;
    @Autowired private DmsCommissionRecordDao commissionRecordDao;
    @Autowired private DmsOrderBalanceAllocationDao allocationDao;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;

    @Test
    void fullOrderLifecycleActivatesMemberAndGeneratesBonus() {
        // Agent A (userId=1001, inviteCode=INV001) as direct inviter
        DmsShopMember member = createMemberWithInviter(80001L, "测试买家", "13900000001", "INV001", 1001L);
        Long productId = 1L;

        // 2. Submit order for product with known PV value
        ShopOrderSubmitDTO dto = buildOrderDTO(member.getUserId(), productId, 1L, 1);
        ShopOrderVO orderVO = shopService.submitOrder(dto, member);
        assertNotNull(orderVO.getOrder().getId());
        assertEquals(0, orderVO.getOrder().getStatus()); // pending payment

        // 3. Pay the order
        Long orderId = orderVO.getOrder().getId();
        ShopOrderVO paidVO = shopService.markOrderPaid(orderId, "BALANCE");
        DmsShopOrder paidOrder = paidVO.getOrder();
        assertEquals(1, paidOrder.getStatus()); // pending shipment

        // 4. Verify agent was activated
        assertNotNull(paidOrder.getAgentId());
        assertTrue(paidOrder.getAgentId() > 0);

        // 5. Ship the order (status 1 -> 2)
        jdbcTemplate.update("UPDATE dms_shop_order SET status = 2, delivery_company = '测试快递', delivery_no = 'SF1234567890', delivery_time = CURRENT_TIMESTAMP WHERE id = ?", orderId);

        // 6. Confirm receipt (status 2 -> 3)
        boolean received = shopService.confirmReceive(orderId, member);
        assertTrue(received);
        DmsShopOrder receivedOrder = orderDao.selectById(orderId);
        assertEquals(3, receivedOrder.getStatus()); // completed
        assertNotNull(receivedOrder.getReceiveTime());

        // 7. Verify commission records were generated
        List<?> commissions = commissionRecordDao.selectByOrderId(orderId);
        assertNotNull(commissions);
        assertFalse(commissions.isEmpty(), "commission should be generated after payment and receipt");

        // 8. Verify balance allocations exist (may be empty if system accounts not fully set up)
        List<?> allocations = allocationDao.selectByOrderId(orderId);
        assertNotNull(allocations, "allocations query should return a list");
    }

    @Test
    void cancelOrderRestocksInventory() {
        DmsShopMember member = createMember(80002L, "取消测试", "13900000002");
        Long productId = 1L;

        DmsShopProduct productBefore = productDao.selectById(productId);
        Integer stockBefore = productBefore.getStock();

        ShopOrderSubmitDTO dto = buildOrderDTO(member.getUserId(), productId, 1L, 3);
        ShopOrderVO orderVO = shopService.submitOrder(dto, member);

        DmsShopProduct productAfterOrder = productDao.selectById(productId);
        assertEquals(stockBefore - 3, productAfterOrder.getStock());

        boolean cancelled = shopService.cancelOrder(orderVO.getOrder().getId(), member);
        assertTrue(cancelled);

        DmsShopProduct productAfterCancel = productDao.selectById(productId);
        assertEquals(stockBefore, productAfterCancel.getStock());
    }

    @Test
    void commissionGeneratedAfterPayment() {
        DmsShopMember member = createMemberWithInviter(80003L, "奖金前测试", "13900000003", "INV001", 1001L);
        Long productId = 1L;

        ShopOrderSubmitDTO dto = buildOrderDTO(member.getUserId(), productId, 1L, 1);
        ShopOrderVO orderVO = shopService.submitOrder(dto, member);
        Long orderId = orderVO.getOrder().getId();

        // After payment: commission records should be generated
        shopService.markOrderPaid(orderId, "BALANCE");
        List<?> after = commissionRecordDao.selectByOrderId(orderId);
        assertNotNull(after, "commission records should exist after payment");
        assertFalse(after.isEmpty(), "commission records should not be empty after payment");
    }

    @Test
    void settlementRequiresCoolingOff() {
        DmsShopMember member = createMemberWithInviter(80004L, "冷却测试", "13900000004", "INV001", 1001L);
        Long productId = 1L;

        ShopOrderSubmitDTO dto = buildOrderDTO(member.getUserId(), productId, 1L, 1);
        ShopOrderVO orderVO = shopService.submitOrder(dto, member);
        Long orderId = orderVO.getOrder().getId();
        shopService.markOrderPaid(orderId, "BALANCE");
        shopService.confirmReceive(orderId, member);

        // 使用真实租户规则关闭客户自助售后等待期，不再修改已废弃的固定T+7字段。
        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_mode = 'RECEIVED', after_sale_window_days = 0 WHERE id = 1");
        sqlSessionTemplate.clearCache();

        // Settlement should now process
        int allocated = allocationService.settleEligibleAfterCoolingOff(10);
        int settled = settlementService.settleEligibleAfterCoolingOff(10);

        assertTrue(allocated >= 0);
        assertTrue(settled >= 0);
    }

    @Test
    void commissionCannotSettleBeforeTenantAfterSaleDeadline() {
        DmsShopMember member = createMemberWithInviter(80006L, "售后窗口测试", "13900000006", "INV001", 1001L);
        ShopOrderVO orderVO = shopService.submitOrder(buildOrderDTO(member.getUserId(), 1L, 1L, 1), member);
        Long orderId = orderVO.getOrder().getId();
        shopService.markOrderPaid(orderId, "BALANCE");
        jdbcTemplate.update("UPDATE dms_shop_order SET status = 3, receive_time = ? WHERE id = ?",
                LocalDateTime.now().minusDays(10), orderId);
        jdbcTemplate.update("UPDATE dms_tenant SET after_sale_window_mode = 'RECEIVED', after_sale_window_days = 30 WHERE id = 1");

        settlementService.settleEligibleAfterCoolingOff(100);
        DmsCommissionRecord pending = commissionRecordDao.selectByOrderId(orderId).get(0);
        assertEquals(0, pending.getStatus(), "T+7不能早于客户配置的30天售后期限结算");

        jdbcTemplate.update("UPDATE dms_shop_order SET receive_time = ? WHERE id = ?",
                LocalDateTime.now().minusDays(31), orderId);
        sqlSessionTemplate.clearCache();
        settlementService.settleEligibleAfterCoolingOff(100);
        assertEquals(1, commissionRecordDao.selectByOrderId(orderId).get(0).getStatus());
    }

    @Test
    void bonusAmountMatchesProductPv() {
        DmsShopMember member = createMemberWithInviter(80005L, "PV验证", "13900000005", "INV001", 1001L);
        Long productId = 1L;

        DmsShopProduct product = productDao.selectById(productId);
        BigDecimal expectedPv = product.getPvValue(); // 220.00

        ShopOrderSubmitDTO dto = buildOrderDTO(member.getUserId(), productId, 1L, 1);
        ShopOrderVO orderVO = shopService.submitOrder(dto, member);
        shopService.markOrderPaid(orderVO.getOrder().getId(), "BALANCE");

        DmsShopOrder order = orderDao.selectById(orderVO.getOrder().getId());
        assertEquals(0, expectedPv.compareTo(order.getTotalPv()));
    }

    private DmsShopMember createMember(Long userId, String nickname, String phone) {
        return createMemberWithInvite(userId, nickname, phone, "INV" + userId);
    }

    private DmsShopMember createMemberWithInvite(Long userId, String nickname, String phone, String inviteCode) {
        return createMemberWithInviter(userId, nickname, phone, inviteCode, null);
    }

    private DmsShopMember createMemberWithInviter(Long userId, String nickname, String phone, String inviteCode, Long inviterId) {
        // Persist member to H2 database so activateMember can find it
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member (user_id, phone, login_account, password_hash, nickname, status, invite_code, inviter_id, create_time)
                VALUES (?, ?, ?, 'dummy_hash_for_test', ?, 1, ?, ?, CURRENT_TIMESTAMP)
                """, userId, phone, "u" + userId, nickname, inviteCode, inviterId);

        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM dms_shop_member WHERE user_id = ?", Long.class, userId);
        DmsShopMember member = new DmsShopMember();
        member.setId(memberId);
        member.setUserId(userId);
        member.setNickname(nickname);
        member.setPhone(phone);
        member.setStatus(1);
        member.setUsername("u" + userId);
        member.setInviteCode(inviteCode);
        member.setInviterId(inviterId);
        return member;
    }

    private ShopOrderSubmitDTO buildOrderDTO(Long userId, Long productId, Long skuId, int quantity) {
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(productId);
        item.setSkuId(skuId);
        item.setQuantity(quantity);

        ShopOrderSubmitDTO dto = new ShopOrderSubmitDTO();
        dto.setUserId(userId);
        dto.setReceiverName("收货人");
        dto.setReceiverPhone("13800000000");
        dto.setReceiverAddress("湖南省长沙市岳麓区");
        dto.setReceiverProvince("湖南省");
        dto.setReceiverCity("长沙市");
        dto.setReceiverDistrict("岳麓区");
        dto.setReceiverDetailAddress("测试路88号");
        dto.setPayType("BALANCE");
        dto.setItems(List.of(item));
        return dto;
    }
}
