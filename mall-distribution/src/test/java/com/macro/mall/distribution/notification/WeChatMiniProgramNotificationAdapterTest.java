package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsMiniProgramSubscriptionGrant;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.service.WeChatSubscriptionService;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeChatMiniProgramNotificationAdapterTest {
    @Mock private WeChatSubscriptionService subscriptionService;
    @Mock private DmsWechatMiniProgramIdentityDao identityDao;
    @Mock private WeChatMiniProgramGateway gateway;
    private WeChatMiniProgramNotificationAdapter adapter;
    private WeChatMiniProgramProperties.SubscriptionTemplate template;
    private ExternalNotificationContext context;

    @BeforeEach
    void setUp() {
        WeChatMiniProgramProperties properties = new WeChatMiniProgramProperties();
        properties.setEnabled(true);
        properties.setSubscribeMessageEnabled(true);
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("customer-secret-value");
        template = new WeChatMiniProgramProperties.SubscriptionTemplate();
        template.setTemplateId("template_1234567890abcdef123456");
        template.setPage("pages/orders/index");
        template.setStatusKey("phrase1");
        template.setTimeKey("time2");
        template.setRemarkKey("thing3");
        properties.setSubscriptionTemplates(Map.of("ORDER_SHIPPED", template));
        adapter = new WeChatMiniProgramNotificationAdapter(properties, subscriptionService, identityDao, gateway);
        context = new ExternalNotificationContext();
        context.setTaskId(7L);
        context.setTenantId(1L);
        context.setMemberId(8L);
        context.setUserId(80L);
        context.setEventType("ORDER_SHIPPED");
        context.setSummary("您的订单已发货，请查看物流进度");
        when(subscriptionService.template("ORDER_SHIPPED")).thenReturn(template);
    }

    @Test
    void consumesExactlyOneGrantAfterOfficialDeliveryAccepted() {
        identity();
        when(subscriptionService.reserve(1L, 8L, template.getTemplateId(), 7L))
                .thenReturn(new DmsMiniProgramSubscriptionGrant());
        when(gateway.sendSubscribeMessage(any())).thenReturn(new WeChatMiniProgramGateway.SubscribeMessageResult(0));

        DeliveryResult result = adapter.send(context, "task-key");

        assertEquals(DeliveryState.DELIVERED, result.state());
        verify(subscriptionService).consume(1L, 7L);
        ArgumentCaptor<WeChatMiniProgramGateway.SubscribeMessageCommand> command =
                ArgumentCaptor.forClass(WeChatMiniProgramGateway.SubscribeMessageCommand.class);
        verify(gateway).sendSubscribeMessage(command.capture());
        assertEquals("openid-secret", command.getValue().openId());
        assertEquals("已发货", command.getValue().data().get("phrase1"));
        assertTrue(command.getValue().data().get("thing3").codePointCount(0,
                command.getValue().data().get("thing3").length()) <= 20);
    }

    @Test
    void invalidWechatPermissionInvalidatesReservedGrant() {
        identity();
        when(subscriptionService.reserve(1L, 8L, template.getTemplateId(), 7L))
                .thenReturn(new DmsMiniProgramSubscriptionGrant());
        when(gateway.sendSubscribeMessage(any())).thenReturn(new WeChatMiniProgramGateway.SubscribeMessageResult(43101));

        assertEquals(DeliveryState.PERMANENT, adapter.send(context, "task-key").state());
        verify(subscriptionService).invalidate(1L, 7L);
    }

    @Test
    void unknownTransportResultIsNotRetriedWithSameSingleUsePermission() {
        identity();
        when(subscriptionService.reserve(1L, 8L, template.getTemplateId(), 7L))
                .thenReturn(new DmsMiniProgramSubscriptionGrant());
        when(gateway.sendSubscribeMessage(any())).thenThrow(new IllegalStateException("network"));

        assertEquals(DeliveryState.UNKNOWN, adapter.send(context, "task-key").state());
        verify(subscriptionService).consume(1L, 7L);
    }

    private void identity() {
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setUserId(80L);
        identity.setOpenId("openid-secret");
        when(identityDao.selectByMember(any(), any(), any())).thenReturn(identity);
    }
}
