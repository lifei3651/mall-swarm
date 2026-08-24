package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.DmsMemberMessage;
import com.macro.mall.distribution.entity.DmsMessageTemplate;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.impl.MemberMessageServiceImpl;
import com.macro.mall.distribution.vo.MessageUnreadCountVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberMessageAccessTest {
    @Mock MemberMessageWriter writer; @Mock DmsMemberMessageDao messageDao;
    @Mock DmsMessageTemplateDao templateDao; @Mock DmsMessageChannelConfigDao channelDao;
    @Mock DmsMessageDeliveryTaskDao deliveryDao; @Mock OperationLogService operationLogService;
    MemberMessageServiceImpl service; DmsShopMember member;
    @BeforeEach void setup() {
        service = new MemberMessageServiceImpl(writer,messageDao,templateDao,channelDao,deliveryDao,operationLogService);
        member = new DmsShopMember(); member.setId(8L); member.setUserId(80L);
    }
    @AfterEach void clearTenant() { TenantContext.clear(); }
    @Test void listExposureNeverMarksRead() {
        when(messageDao.selectPage(1L,8L,null)).thenReturn(List.of()); service.list(member,null,1,20);
        verify(messageDao,never()).markRead(anyLong(),anyLong(),anyLong());
    }
    @Test void detailUsesTenantAndMemberOwnershipThenMarksRead() {
        DmsMemberMessage owned = new DmsMemberMessage(); owned.setId(3L);
        when(messageDao.selectOwned(1L,8L,3L)).thenReturn(owned);
        assertEquals(3L,service.detail(member,3L).getId()); verify(messageDao).markRead(1L,8L,3L);
    }
    @Test void idorCannotReadOrMarkAnotherMembersMessage() {
        when(messageDao.selectOwned(1L,8L,99L)).thenReturn(null);
        assertThrows(ApiException.class,()->service.detail(member,99L));
        assertThrows(ApiException.class,()->service.markRead(member,99L));
        verify(messageDao,never()).markRead(1L,8L,99L);
    }
    @Test void tenantBoundaryIsAppliedEvenWhenMemberAndMessageNumbersMatch() {
        TenantContext.setTenantId(2L);
        when(messageDao.selectOwned(2L,8L,3L)).thenReturn(null);
        assertThrows(ApiException.class,()->service.detail(member,3L));
        verify(messageDao, never()).selectOwned(1L,8L,3L);
        verify(messageDao, never()).markRead(anyLong(),anyLong(),anyLong());
    }
    @Test void unreadContainsOnlyFivePersonalCategoriesAndNeverAnnouncements() {
        MessageUnreadCountVO order = new MessageUnreadCountVO(); order.setCategory("ORDER_LOGISTICS"); order.setUnreadCount(2L);
        when(messageDao.countUnreadByCategory(1L,8L)).thenReturn(List.of(order));
        var unread=service.unread(member); assertEquals(2L,unread.getTotal()); assertEquals(5,unread.getCategories().size());
        assertFalse(unread.getCategories().containsKey("NOTICE"));
    }
    @Test void categoryAndAllReadAreServerSideAndSharedAcrossSurfaces() {
        when(messageDao.markAllRead(1L,8L,"WALLET_FUNDS")).thenReturn(3);
        when(messageDao.markAllRead(1L,8L,null)).thenReturn(7);
        assertEquals(3,service.markAllRead(member,"WALLET_FUNDS")); assertEquals(7,service.markAllRead(member,null));
    }
    @Test void futureTemplatesRejectSensitiveFactsAndDynamicVariables() {
        DmsMessageTemplate current = template("安全标题", "登录后查看", "请进入对应业务查看详情");
        when(templateDao.selectById(1L, 9L)).thenReturn(current);

        assertThrows(ApiException.class, () -> service.updateTemplate(9L,
                template("资金变动 100.00 元", "登录后查看", "请进入钱包查看")));
        assertThrows(ApiException.class, () -> service.updateTemplate(9L,
                template("账户提醒", "验证码 123456", "手机号 13800138000")));
        assertThrows(ApiException.class, () -> service.updateTemplate(9L,
                template("账户提醒", "登录后查看", "银行卡 6222021234567890")));
        assertThrows(ApiException.class, () -> service.updateTemplate(9L,
                template("订单提醒", "登录后查看", "订单金额 {{amount}}")));
        verify(templateDao, never()).update(any());
    }
    private DmsMessageTemplate template(String title, String summary, String content) {
        DmsMessageTemplate value = new DmsMessageTemplate();
        value.setTitleTemplate(title); value.setSummaryTemplate(summary); value.setContentTemplate(content);
        value.setEnabled(1); return value;
    }
}
