package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OptionalInvitationBindingTest {
    @Autowired ShopAuthService auth;
    @Autowired ShopService shop;
    @Autowired DmsShopMemberDao members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SmsVerificationService sms;
    @MockitoBean LoginCaptchaService captcha;

    void modes(int invite, int fallback, String code) {
        jdbc.update("UPDATE dms_tenant SET invitation_enabled=?,default_inviter_enabled=?,default_inviter_code=?,promotion_join_mode='DISABLED' WHERE id=1", invite, fallback, code);
    }
    DmsShopMember master(String suffix) {
        AdminMemberCreateDTO dto = new AdminMemberCreateDTO();
        dto.setPhone("1397711"+suffix); dto.setUsername("invite_master_"+suffix);
        dto.setNickname("测试主账号"); dto.setActivateDistribution(false);
        var member = auth.createAdminMember(dto);
        return members.selectById(member.getId());
    }
    DmsShopMember registered(String suffix, String code) {
        ShopRegisterDTO dto = new ShopRegisterDTO();
        dto.setPhone("1397722"+suffix); dto.setUsername("invite_customer_"+suffix);
        dto.setPassword("InviteLocalTest123!"); dto.setSmsCode("123456");
        dto.setCaptchaId("test-captcha"); dto.setCaptchaCode("ABCD"); dto.setInviteCode(code);
        return members.selectById(auth.registerPublic(dto).getMember().getId());
    }
    @Test void firstCustomerRegistersWithoutInviteAndHasOwnShareCode() {
        modes(1,0,null);
        var member = registered("0001",null);
        assertNull(member.getInviterId()); assertEquals(0,member.getTeamOptIn());
        assertTrue(member.getInviteCode().matches("[A-Z0-9]{8}"));
        assertEquals(member.getInviteCode(),shop.getInviteInfo(member).get("inviteCode"));
    }
    @Test void validShareWinsOverOptionalDefaultWhileNoShareUsesDefault() {
        var root=master("0002"); var sharer=master("0003"); modes(1,1,root.getInviteCode());
        assertEquals(sharer.getUserId(),registered("0002",sharer.getInviteCode()).getInviterId());
        assertEquals(root.getUserId(),registered("0003",null).getInviterId());
        assertEquals(root.getUserId(),members.selectById(auth.loginOrRegisterWechat("13977220004",null).getMember().getId()).getInviterId());
    }
    @Test void laterShareBindsOnceAndDefaultDoesNotRetroactivelyAssignOldAccounts() {
        modes(1,0,null); var member=registered("0005",null);
        var root=master("0004"); var other=master("0005"); modes(1,1,root.getInviteCode());
        assertNull(members.selectById(member.getId()).getInviterId());
        assertEquals("BOUND",auth.bindSharedInvitation(member,other.getInviteCode()));
        assertEquals("ALREADY_BOUND",auth.bindSharedInvitation(member,root.getInviteCode()));
        assertEquals(other.getUserId(),members.selectById(member.getId()).getInviterId());
    }
    @Test void invitationOffBlocksNewBindingButKeepsLoginAndExistingRelation() {
        modes(1,0,null);var root=master("0006");var bound=registered("0006",root.getInviteCode());
        modes(0,1,root.getInviteCode());var plain=registered("0007",null);
        assertNull(plain.getInviterId());assertEquals("DISABLED",auth.bindSharedInvitation(plain,root.getInviteCode()));
        assertEquals(bound.getId(),auth.loginOrRegisterWechat(bound.getPhone(),null).getMember().getId());
        assertEquals(root.getUserId(),members.selectById(bound.getId()).getInviterId());
    }
    @Test void selfSystemInactiveAndCyclesCannotBecomeInviters() {
        modes(1,0,null);var a=registered("0008",null);var b=registered("0009",null);
        assertEquals("SELF",auth.bindSharedInvitation(a,a.getInviteCode()));
        assertEquals("BOUND",auth.bindSharedInvitation(b,a.getInviteCode()));
        assertThrows(ApiException.class,()->auth.bindSharedInvitation(a,b.getInviteCode()));
        var system=master("0007");jdbc.update("UPDATE dms_shop_member SET system_account=1 WHERE id=?",system.getId());
        assertThrows(ApiException.class,()->auth.bindSharedInvitation(a,system.getInviteCode()));
        var inactive=master("0008");jdbc.update("UPDATE dms_shop_member SET status=0 WHERE id=?",inactive.getId());
        assertThrows(ApiException.class,()->auth.bindSharedInvitation(a,inactive.getInviteCode()));
        assertNull(members.selectById(a.getId()).getInviterId());
    }
    @Test void conditionalWriteCannotReplaceAnExistingRelation() {
        modes(1,0,null);var a=master("0009");var b=master("0010");var m=registered("0010",a.getInviteCode());
        assertEquals(0,members.bindInviterOnce(m.getId(),b.getUserId()));
        assertEquals(a.getUserId(),members.selectById(m.getId()).getInviterId());
    }
    @Test void qualifiedRootBindsOnceWithoutChangingHistoricalInviters() {
        modes(1,0,null);var parent=master("0013");var child=registered("0013",null);
        var parentAgent=auth.activateMember(parent.getUserId(),1,"测试开通");
        var childAgent=auth.activateMember(child.getUserId(),1,"测试开通");
        assertNull(childAgent.getParentId());
        assertEquals("BOUND",auth.bindSharedInvitation(child,parent.getInviteCode()));
        assertEquals(parent.getUserId(),members.selectById(child.getId()).getInviterId());
        assertEquals(parentAgent.getId(),jdbc.queryForObject("SELECT parent_id FROM dms_agent WHERE id=?",Long.class,childAgent.getId()));
    }
    @Test void legacyPromotionParentIsNotReplacedWhenCustomerFieldIsEmpty() {
        modes(1,0,null);var parent=master("0014");var other=master("0015");var child=registered("0014",parent.getInviteCode());
        var parentAgent=auth.activateMember(parent.getUserId(),1,"测试开通");
        var childAgent=auth.activateMember(child.getUserId(),1,"测试开通");
        jdbc.update("UPDATE dms_shop_member SET inviter_id=NULL WHERE id=?",child.getId());
        assertEquals("ALREADY_BOUND",auth.bindSharedInvitation(child,other.getInviteCode()));
        assertEquals(parentAgent.getId(),jdbc.queryForObject("SELECT parent_id FROM dms_agent WHERE id=?",Long.class,childAgent.getId()));
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentSharesOnlyBindOneInviter() throws Exception {
        var before=jdbc.queryForMap("SELECT invitation_enabled,default_inviter_enabled,default_inviter_code,promotion_join_mode FROM dms_tenant WHERE id=1");
        modes(1,0,null); var a=master("0011");var b=master("0012");var member=registered("0011",null);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            Callable<String> left=()->{gate.await();return auth.bindSharedInvitation(member,a.getInviteCode());};
            Callable<String> right=()->{gate.await();return auth.bindSharedInvitation(member,b.getInviteCode());};
            var first=pool.submit(left);var second=pool.submit(right);gate.countDown();
            var results=java.util.List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter("BOUND"::equals).count());
            assertEquals(1,results.stream().filter("ALREADY_BOUND"::equals).count());
            assertTrue(java.util.List.of(a.getUserId(),b.getUserId()).contains(members.selectById(member.getId()).getInviterId()));
        } finally {
            pool.shutdownNow();
            for (var row:java.util.List.of(a,b,member)) {
                jdbc.update("DELETE FROM dms_shop_member_session WHERE member_id=?",row.getId());
                jdbc.update("DELETE FROM dms_shop_member WHERE id=?",row.getId());
            }
            jdbc.update("UPDATE dms_tenant SET invitation_enabled=?,default_inviter_enabled=?,default_inviter_code=?,promotion_join_mode=? WHERE id=1",before.get("invitation_enabled"),before.get("default_inviter_enabled"),before.get("default_inviter_code"),before.get("promotion_join_mode"));
        }
    }
}
