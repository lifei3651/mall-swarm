package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsMessageRecipientAuthorizationDao;
import com.macro.mall.distribution.dao.DmsMiniProgramSubscriptionGrantDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.dto.WeChatSubscriptionGrantDTO;
import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import com.macro.mall.distribution.entity.DmsMiniProgramSubscriptionGrant;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.notification.ExternalNotificationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeChatSubscriptionServiceTest {
    @Mock private DmsMiniProgramSubscriptionGrantDao grantDao;
    @Mock private DmsWechatMiniProgramIdentityDao identityDao;
    @Mock private DmsMessageRecipientAuthorizationDao authorizationDao;
    private WeChatSubscriptionService service;
    private WeChatMiniProgramProperties properties;
    private ExternalNotificationProperties external;
    private DmsShopMember member;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        properties = new WeChatMiniProgramProperties();
        properties.setEnabled(true);
        properties.setSubscribeMessageEnabled(true);
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("customer-secret-value");
        WeChatMiniProgramProperties.SubscriptionTemplate template = new WeChatMiniProgramProperties.SubscriptionTemplate();
        template.setTemplateId("template_1234567890abcdef123456");
        template.setTitle("订单发货提醒");
        template.setStatusKey("phrase1");
        template.setTimeKey("time2");
        template.setRemarkKey("thing3");
        properties.setSubscriptionTemplates(Map.of("ORDER_SHIPPED", template));
        external = new ExternalNotificationProperties();
        external.setEnabled(true);
        external.setWorkerEnabled(true);
        service = new WeChatSubscriptionService(properties, external, grantDao, identityDao, authorizationDao);
        member = new DmsShopMember();
        member.setId(8L);
        member.setUserId(80L);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    @Test
    void recordsOnlyAcceptedConfiguredTemplateWithoutStoringRawTemplateOrOpenId() {
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setMemberId(8L);
        identity.setUserId(80L);
        identity.setOpenIdHash("a".repeat(64));
        when(identityDao.selectByMember(any(), any(), any())).thenReturn(identity);
        when(grantDao.countAvailable(eq(1L), eq(8L), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        WeChatSubscriptionGrantDTO input = new WeChatSubscriptionGrantDTO();
        input.setRequestId("request_1234567890");
        input.setAcceptedTemplateIds(List.of("template_1234567890abcdef123456"));

        var result = service.record(member, input);

        ArgumentCaptor<DmsMiniProgramSubscriptionGrant> grant = ArgumentCaptor.forClass(DmsMiniProgramSubscriptionGrant.class);
        verify(grantDao).insertIgnore(grant.capture());
        assertEquals(64, grant.getValue().getTemplateIdHash().length());
        assertNotEquals(input.getAcceptedTemplateIds().get(0), grant.getValue().getTemplateIdHash());
        ArgumentCaptor<DmsMessageRecipientAuthorization> authorization = ArgumentCaptor.forClass(DmsMessageRecipientAuthorization.class);
        verify(authorizationDao).insert(authorization.capture());
        assertEquals("a".repeat(64), authorization.getValue().getEndpointHash());
        assertEquals(1, result.get(0).getAvailableGrants());
    }

    @Test
    void rejectsDuplicateOrUnconfiguredTemplateBeforeWriting() {
        WeChatSubscriptionGrantDTO duplicate = new WeChatSubscriptionGrantDTO();
        duplicate.setRequestId("request_1234567890");
        duplicate.setAcceptedTemplateIds(List.of("unknown_template_123456789", "unknown_template_123456789"));

        assertThrows(ApiException.class, () -> service.record(member, duplicate));
        verify(grantDao, never()).insertIgnore(any());
    }

    @Test
    void readinessRequiresExplicitExternalWorkerGate() {
        assertTrue(service.ready());
        external.setWorkerEnabled(false);
        assertEquals(List.of(), service.publicTemplates());
    }

    @Test
    void reportsEachMissingTemplateInsteadOfClaimingAllFourReady() {
        var rows = service.readiness();
        assertEquals(4, rows.size());
        assertEquals(1, rows.stream().filter(row -> row.templateConfigured()).count());
        assertEquals(1, rows.stream().filter(row -> row.runtimeReady()).count());
        assertEquals("WITHDRAW_PAID", rows.get(3).eventType());
        external.setWorkerEnabled(false);
        assertEquals(0, service.readiness().stream().filter(row -> row.runtimeReady()).count());
        assertEquals(1, service.readiness().stream().filter(row -> row.templateConfigured()).count());
    }

    @Test
    void rejectsFourTemplatesEvenIfCalledWithoutControllerValidation() {
        WeChatSubscriptionGrantDTO input = new WeChatSubscriptionGrantDTO();
        input.setRequestId("request_1234567890");
        input.setAcceptedTemplateIds(List.of("a", "b", "c", "d"));
        assertThrows(ApiException.class, () -> service.record(member, input));
        verify(grantDao, never()).insertIgnore(any());
        verify(identityDao, never()).selectByMember(any(), any(), any());
    }

    @Test
    void invalidTemplateTargetIsNotReportedReadyOrOfferedForSubscription() {
        properties.getSubscriptionTemplates().get("ORDER_SHIPPED").setPage("https://not-a-mini-page.example");
        assertEquals(0, service.readiness().stream().filter(row -> row.templateConfigured()).count());
        assertEquals(List.of(), service.publicTemplates());
    }
}
