package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.ShopServiceTicketAdminActionDTO;
import com.macro.mall.distribution.dto.ShopServiceTicketCreateDTO;
import com.macro.mall.distribution.entity.DmsAdminUser;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopServiceTicketDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShopServiceTicketWorkflowTest {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ShopServiceTicketService ticketService;

    private DmsShopMember member;
    private DmsShopMember otherMember;
    private DmsAdminUser platformAdmin;
    private DmsAdminUser merchantAdmin;
    private DmsAdminUser otherMerchantAdmin;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_member
                (id,user_id,phone,login_account,password_hash,nickname,invite_code,status,system_account,team_opt_in)
                VALUES (990101,990101,'13900000101','ticket-member-1','hash','工单会员一','TK990101',1,0,0),
                       (990102,990102,'13900000102','ticket-member-2','hash','工单会员二','TK990102',1,0,0)
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,merchant_id,merchant_name,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,business_type,status,pay_type)
                VALUES (990111,'TICKET-ORDER-1',1,990121,'测试商户一',990101,'收货人','13900000101','测试地址',100,0,0,100,0,50,'NORMAL',3,'BALANCE')
                """);
        jdbcTemplate.update("""
                INSERT INTO dms_shop_after_sale
                (id,after_sale_no,order_id,order_no,member_id,user_id,apply_type,refund_amount,product_refund_amount,freight_refund_amount,refund_quantity,reason,status)
                VALUES (990131,'TICKET-AFTER-1',990111,'TICKET-ORDER-1',990101,990101,1,10,10,0,1,'售后争议测试',2)
                """);
        member = member(990101L, 990101L, "ticket-member-1");
        otherMember = member(990102L, 990102L, "ticket-member-2");
        platformAdmin = admin(990141L, null);
        merchantAdmin = admin(990142L, 990121L);
        otherMerchantAdmin = admin(990143L, 990122L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void linkedAfterSaleTicketIsOwnerAndMerchantScopedThroughFullConversation() {
        ShopServiceTicketCreateDTO create = new ShopServiceTicketCreateDTO();
        create.setType("AFTER_SALE_DISPUTE");
        create.setSubject("售后处理结果需要复核");
        create.setContent("退款结果与实际退回商品情况不一致，请协助复核。");
        create.setOrderId(990111L);
        create.setAfterSaleId(990131L);

        ShopServiceTicketDetailVO created = ticketService.create(member, create);
        Long ticketId = created.getTicket().getId();
        assertNotNull(ticketId);
        assertEquals("OPEN", created.getTicket().getStatus());
        assertEquals(1, created.getReplies().size());
        assertThrows(ApiException.class, () -> ticketService.memberDetail(otherMember, ticketId));
        assertThrows(ApiException.class, () -> ticketService.adminDetail(otherMerchantAdmin, ticketId));
        assertNotNull(ticketService.adminDetail(merchantAdmin, ticketId));

        ShopServiceTicketAdminActionDTO waitMember = action("请补充退回商品的签收情况。", "WAITING_MEMBER");
        ShopServiceTicketDetailVO adminReplied = ticketService.adminReply(merchantAdmin, ticketId, waitMember);
        assertEquals("WAITING_MEMBER", adminReplied.getTicket().getStatus());
        assertNotNull(adminReplied.getTicket().getFirstResponseAt());
        assertEquals("MEMBER", adminReplied.getTicket().getNextActionParty());

        ShopServiceTicketDetailVO reopened = ticketService.memberReply(member, ticketId, "商品已经签收，请继续核对。");
        assertEquals("OPEN", reopened.getTicket().getStatus());
        assertEquals("CUSTOMER_SERVICE", reopened.getTicket().getNextActionParty());

        ShopServiceTicketDetailVO resolved = ticketService.adminReply(platformAdmin, ticketId,
                action("已经完成复核，请确认处理结果。", "RESOLVED"));
        assertEquals("RESOLVED", resolved.getTicket().getStatus());
        assertThrows(ApiException.class, () -> ticketService.create(member, create));

        ShopServiceTicketDetailVO closed = ticketService.memberClose(member, ticketId);
        assertEquals("CLOSED", closed.getTicket().getStatus());
        assertTrue(closed.getReplies().size() >= 5);
        assertThrows(ApiException.class, () -> ticketService.memberReply(member, ticketId, "关闭后继续回复"));
    }

    @Test
    void generalAndAccountTicketsRemainPlatformOnlyAndValidateSensitiveLinkingRules() {
        ShopServiceTicketCreateDTO general = new ShopServiceTicketCreateDTO();
        general.setType("ACCOUNT");
        general.setSubject("账号安全设置咨询");
        general.setContent("需要了解如何修改登录密码。");
        ShopServiceTicketDetailVO created = ticketService.create(member, general);

        assertNotNull(ticketService.adminDetail(platformAdmin, created.getTicket().getId()));
        assertThrows(ApiException.class, () -> ticketService.adminDetail(merchantAdmin, created.getTicket().getId()));

        general.setOrderId(990111L);
        assertThrows(ApiException.class, () -> ticketService.create(member, general));

        ShopServiceTicketCreateDTO invalidDispute = new ShopServiceTicketCreateDTO();
        invalidDispute.setType("AFTER_SALE_DISPUTE");
        invalidDispute.setSubject("未关联售后");
        invalidDispute.setContent("售后争议必须明确关联售后记录。");
        assertThrows(ApiException.class, () -> ticketService.create(member, invalidDispute));

        ShopServiceTicketCreateDTO foreignOrder = new ShopServiceTicketCreateDTO();
        foreignOrder.setType("CONSULTATION");
        foreignOrder.setSubject("越权订单测试");
        foreignOrder.setContent("不能关联其他会员订单。");
        foreignOrder.setOrderId(990111L);
        assertThrows(ApiException.class, () -> ticketService.create(otherMember, foreignOrder));
    }

    private DmsShopMember member(Long id, Long userId, String username) {
        DmsShopMember value = new DmsShopMember();
        value.setId(id); value.setUserId(userId); value.setUsername(username); value.setStatus(1); value.setSystemAccount(0);
        return value;
    }

    private DmsAdminUser admin(Long id, Long merchantId) {
        DmsAdminUser value = new DmsAdminUser();
        value.setId(id); value.setUsername("admin-" + id); value.setNickname("客服" + id); value.setMerchantId(merchantId); value.setStatus(1);
        return value;
    }

    private ShopServiceTicketAdminActionDTO action(String content, String nextStatus) {
        ShopServiceTicketAdminActionDTO value = new ShopServiceTicketAdminActionDTO();
        value.setContent(content); value.setNextStatus(nextStatus); return value;
    }
}
