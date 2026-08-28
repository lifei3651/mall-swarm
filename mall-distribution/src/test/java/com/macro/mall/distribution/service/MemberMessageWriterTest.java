package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.notification.ExternalNotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberMessageWriterTest {
    @Mock DmsShopMemberDao memberDao;
    @Mock DmsMemberMessageDao messageDao;
    @Mock DmsMessageTemplateDao templateDao;
    @Mock DmsMessageChannelConfigDao channelDao;
    @Mock DmsMessageDeliveryTaskDao deliveryDao;
    @Mock ApplicationEventPublisher eventPublisher;
    MemberMessageWriter writer;

    @BeforeEach void setup() { writer = new MemberMessageWriter(memberDao, messageDao, templateDao, channelDao, deliveryDao, eventPublisher, new ExternalNotificationProperties()); }

    @Test
    void stableEventKeyIsIdempotentSnapshotIsImmutableAndExternalChannelsStayDisabled() {
        DmsShopMember member = new DmsShopMember(); member.setId(7L); member.setStatus(1);
        DmsMessageTemplate template = new DmsMessageTemplate(); template.setEnabled(1);
        template.setTitleTemplate("支付成功"); template.setSummaryTemplate("订单状态已更新"); template.setContentTemplate("请登录查看");
        DmsMessageChannelConfig channels = new DmsMessageChannelConfig(); channels.setInAppEnabled(1);
        when(memberDao.selectByUserId(70L)).thenReturn(member);
        when(templateDao.selectByEventType(1L, "ORDER_PAID")).thenReturn(template);
        when(channelDao.selectByEventType(1L, "ORDER_PAID")).thenReturn(channels);
        when(messageDao.insertIgnore(any())).thenAnswer(invocation -> {
            DmsMemberMessage saved = invocation.getArgument(0); saved.setId(101L); return 1;
        }).thenReturn(0);
        MemberMessageEvent event = new MemberMessageEvent(1L,70L,"ORDER_PAID:9","ORDER_PAID",
                "ORDER_LOGISTICS","ORDER",9L,null, LocalDateTime.now());

        writer.write(event);
        template.setTitleTemplate("后台后来修改的标题");
        writer.write(event);

        ArgumentCaptor<DmsMemberMessage> message = ArgumentCaptor.forClass(DmsMemberMessage.class);
        verify(messageDao, times(2)).insertIgnore(message.capture());
        assertEquals("支付成功", message.getAllValues().get(0).getTitle());
        verify(eventPublisher, times(1)).publishEvent(any(MemberMessageCreatedEvent.class));
        ArgumentCaptor<DmsMessageDeliveryTask> tasks = ArgumentCaptor.forClass(DmsMessageDeliveryTask.class);
        verify(deliveryDao, times(4)).insertIgnore(tasks.capture());
        assertEquals("SUCCESS", tasks.getAllValues().get(0).getStatus());
        assertFalse(tasks.getAllValues().stream().filter(t -> !"IN_APP".equals(t.getChannel()))
                .anyMatch(t -> !"SUPPRESSED".equals(t.getStatus())));
    }

    @Test
    void allCategoriesAndServiceTicketTargetPersistWhileExternalRecordFailureCannotLoseInAppMessage() {
        DmsShopMember member = new DmsShopMember(); member.setId(7L); member.setStatus(1);
        DmsMessageTemplate template = new DmsMessageTemplate(); template.setEnabled(1);
        template.setTitleTemplate("安全标题"); template.setSummaryTemplate("登录后查看"); template.setContentTemplate("登录后查看详情");
        DmsMessageChannelConfig channels = new DmsMessageChannelConfig(); channels.setInAppEnabled(1);
        when(memberDao.selectByUserId(70L)).thenReturn(member);
        when(templateDao.selectByEventType(eq(1L), anyString())).thenReturn(template);
        when(channelDao.selectByEventType(eq(1L), anyString())).thenReturn(channels);
        when(messageDao.insertIgnore(any())).thenAnswer(invocation -> {
            DmsMemberMessage saved = invocation.getArgument(0); saved.setId(System.nanoTime()); return 1;
        });
        doAnswer(invocation -> {
            DmsMessageDeliveryTask task = invocation.getArgument(0);
            if ("SMS".equals(task.getChannel())) throw new IllegalStateException("provider record unavailable");
            return 1;
        }).when(deliveryDao).insertIgnore(any());
        List<String[]> cases = List.of(
                new String[]{"ORDER_LOGISTICS", "ORDER_PAID", "ORDER"},
                new String[]{"AFTER_SALE_REFUND", "AFTER_SALE_UPDATED", "AFTER_SALE"},
                new String[]{"WALLET_FUNDS", "WALLET_FLOW", "WALLET"},
                new String[]{"ACCOUNT_SECURITY", "LOGIN_PASSWORD_CHANGED", "ACCOUNT_SECURITY"},
                new String[]{"SERVICE", "SERVICE_NOTICE", "NONE"},
                new String[]{"SERVICE", "SERVICE_NOTICE", "SERVICE_TICKET"});
        for (int i = 0; i < cases.size(); i++) {
            String[] item = cases.get(i);
            Long targetId = "NONE".equals(item[2]) ? null : 100L + i;
            Long targetParentId = "AFTER_SALE".equals(item[2]) ? 10L : null;
            MemberMessageEvent event = new MemberMessageEvent(1L, 70L, item[1] + ":" + i, item[1],
                    item[0], item[2], targetId, targetParentId, LocalDateTime.now());
            assertDoesNotThrow(() -> writer.write(event));
        }
        verify(messageDao, times(6)).insertIgnore(any());
        verify(eventPublisher, times(6)).publishEvent(any(MemberMessageCreatedEvent.class));
    }

    @Test
    void controlledBusinessTargetsRejectMissingIdentifiersAndArbitraryTypes() {
        writer.write(new MemberMessageEvent(1L, 70L, "ORDER_PAID:9", "ORDER_PAID",
                "ORDER_LOGISTICS", "ORDER", null, null, LocalDateTime.now()));
        writer.write(new MemberMessageEvent(1L, 70L, "AFTER_SALE:9", "AFTER_SALE_UPDATED",
                "AFTER_SALE_REFUND", "AFTER_SALE", 9L, null, LocalDateTime.now()));
        writer.write(new MemberMessageEvent(1L, 70L, "UNSAFE:9", "SERVICE_NOTICE",
                "SERVICE", "URL", 9L, null, LocalDateTime.now()));

        verifyNoInteractions(memberDao, messageDao, templateDao, channelDao, deliveryDao, eventPublisher);
    }
}
