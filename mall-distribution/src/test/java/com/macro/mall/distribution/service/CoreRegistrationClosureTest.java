package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopOrderItemDTO;
import com.macro.mall.distribution.dto.ShopOrderSubmitDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 同一用例默认跑 H2，也通过覆盖数据源在无网络 MySQL 结构副本运行。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreRegistrationClosureTest {
    @Autowired private ShopAuthService auth;
    @Autowired private ShopService shop;
    @Autowired private DmsShopMemberDao members;
    @Autowired private DmsAgentDao agents;
    @Autowired private JdbcTemplate jdbc;
    // 只替代验证凭据的外部/一次性边界，会员、会话、邀请和订单均真实写入隔离库。
    @MockitoBean private SmsVerificationService sms;
    @MockitoBean private LoginCaptchaService captcha;

    @Test
    void invitationCanCloseWithoutLosingOldRelationsOrBlockingOrdinaryShopping() {
        jdbc.update("UPDATE dms_tenant SET invitation_enabled=1, promotion_join_mode='DISABLED' WHERE id=1");
        AdminMemberCreateDTO inviterRequest = new AdminMemberCreateDTO();
        inviterRequest.setPhone("13988330001");
        inviterRequest.setUsername("closure_inviter");
        inviterRequest.setNickname("隔离邀请人");
        inviterRequest.setActivateDistribution(true);
        inviterRequest.setInitialLevel(1);
        DmsShopMember inviter = auth.createAdminMember(inviterRequest);
        String inviteCode = members.selectById(inviter.getId()).getInviteCode();
        assertNotNull(inviteCode);

        ShopRegisterDTO invitedRequest = registration("13988330002", "closure_invited", inviteCode);
        ShopAuthVO invitedAuth = auth.registerPublic(invitedRequest);
        DmsShopMember invited = members.selectById(invitedAuth.getMember().getId());
        assertEquals(inviter.getUserId(), invited.getInviterId());
        assertEquals(1, invited.getTeamOptIn());
        assertEquals("closure_invited", invited.getUsername());
        assertNull(agents.selectByUserId(invited.getUserId()), "邀请绑定不等于自动开通推广资格");
        assertEquals(invited.getId(), auth.me("Bearer " + invitedAuth.getToken(), "public").getId());

        jdbc.update("UPDATE dms_tenant SET invitation_enabled=0 WHERE id=1");
        int memberCount = jdbc.queryForObject("SELECT COUNT(*) FROM dms_shop_member", Integer.class);
        int sessionCount = jdbc.queryForObject("SELECT COUNT(*) FROM dms_shop_member_session", Integer.class);
        assertThrows(ApiException.class, () -> auth.registerPublic(
                registration("13988330003", "closure_blocked", inviteCode)));
        assertNull(members.selectByPhone("13988330003"));
        assertEquals(memberCount, jdbc.queryForObject("SELECT COUNT(*) FROM dms_shop_member", Integer.class));
        assertEquals(sessionCount, jdbc.queryForObject("SELECT COUNT(*) FROM dms_shop_member_session", Integer.class));
        assertEquals(inviter.getUserId(), members.selectById(invited.getId()).getInviterId());

        ShopAuthVO plainAuth = auth.registerPublic(registration("13988330004", "closure_plain", null));
        DmsShopMember plain = members.selectById(plainAuth.getMember().getId());
        assertNull(plain.getInviterId());
        assertEquals(0, plain.getTeamOptIn());
        assertNull(agents.selectByUserId(plain.getUserId()));
        ShopLoginDTO login = new ShopLoginDTO();
        login.setAccount("closure_plain");
        login.setPassword("ClosureTest123!");
        login.setLoginType("password");
        login.setCaptchaId("isolated-captcha");
        login.setCaptchaCode("ABCD");
        assertEquals(plain.getId(), auth.login(login, "public").getMember().getId());

        int initialStock = jdbc.queryForObject("SELECT stock FROM dms_shop_sku WHERE id=1", Integer.class);
        ShopOrderItemDTO item = new ShopOrderItemDTO();
        item.setProductId(1L);
        item.setSkuId(1L);
        item.setQuantity(1);
        ShopOrderSubmitDTO orderRequest = new ShopOrderSubmitDTO();
        orderRequest.setPayType("ALIPAY");
        orderRequest.setReceiverName("隔离收货人");
        orderRequest.setReceiverPhone(plain.getPhone());
        orderRequest.setReceiverAddress("湖南省长沙市隔离测试地址");
        orderRequest.setItems(List.of(item));
        ShopOrderVO pending = shop.submitOrder(orderRequest, plain);
        assertEquals(0, pending.getOrder().getStatus());
        assertTrue(shop.cancelOrder(pending.getOrder().getId(), plain));
        assertEquals(4, shop.getOrder(pending.getOrder().getId()).getOrder().getStatus());
        assertEquals(initialStock, jdbc.queryForObject("SELECT stock FROM dms_shop_sku WHERE id=1", Integer.class));
        assertNull(agents.selectByUserId(plain.getUserId()));
    }

    private ShopRegisterDTO registration(String phone, String account, String inviteCode) {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone(phone);
        dto.setUsername(account);
        dto.setPassword("ClosureTest123!");
        dto.setInviteCode(inviteCode);
        dto.setCaptchaId("isolated-captcha");
        dto.setCaptchaCode("ABCD");
        dto.setSmsCode("123456");
        return dto;
    }
}
