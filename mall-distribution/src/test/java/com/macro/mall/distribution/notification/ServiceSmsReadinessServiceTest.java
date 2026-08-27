package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.dao.DmsMessageChannelConfigDao;
import com.macro.mall.distribution.dao.DmsMessageCostBudgetDao;
import com.macro.mall.distribution.dao.DmsMessageRecipientAuthorizationDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceSmsReadinessServiceTest {
    @Mock DmsMessageChannelConfigDao channelDao;
    @Mock DmsMessageCostBudgetDao budgetDao;
    @Mock DmsMessageRecipientAuthorizationDao authorizationDao;
    @Mock DmsTenantDao tenantDao;
    private ExternalNotificationProperties external;
    private AliyunNotificationSmsProperties sms;
    private ServiceSmsReadinessService service;

    @BeforeEach
    void setup() {
        external = new ExternalNotificationProperties();
        sms = new AliyunNotificationSmsProperties();
        service = new ServiceSmsReadinessService(channelDao, budgetDao, authorizationDao, tenantDao, external, sms);
        lenient().when(channelDao.selectList(1L)).thenReturn(List.of());
        lenient().when(budgetDao.selectList(1L)).thenReturn(List.of());
    }

    @Test
    void closedDefaultsReturnOnePlainLanguageChecklist() {
        var status = service.evaluate(1L);

        assertFalse(status.isReadyForMemberOptIn());
        assertEquals(6, status.getItems().size());
        assertEquals(6, status.getRequiredTemplateCount());
        assertEquals(0, status.getApprovedTemplateCount());
        assertTrue(status.getItems().stream().anyMatch(item -> "LEGAL".equals(item.code()) && !item.passed()));
        assertTrue(status.getItems().stream().anyMatch(item -> "RUNTIME".equals(item.code()) && !item.passed()));
    }

    @Test
    void everyComplianceProviderTemplateEventBudgetAndRuntimeGateMustPass() {
        DmsTenant tenant = new DmsTenant();
        tenant.setPrivacyPolicy("服务短信仅用于进度提醒，可在消息中心关闭");
        tenant.setThirdPartyServices("阿里云短信服务");
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        external.setEnabled(true);
        external.setWorkerEnabled(true);
        sms.setEnabled(true);
        sms.setAccessKeyId("configured");
        sms.setAccessKeySecret("configured");
        sms.setSignName("审核签名");
        sms.setReceiptSecret("configured");
        ServiceSmsReadinessService.ALLOWED_EVENTS.forEach(event -> sms.getTemplates().put(event, "approved-" + event));
        List<DmsMessageChannelConfig> channels = ServiceSmsReadinessService.ALLOWED_EVENTS.stream().map(event -> {
            DmsMessageChannelConfig value = new DmsMessageChannelConfig();
            value.setEventType(event);
            value.setSmsEnabled(1);
            return value;
        }).toList();
        when(channelDao.selectList(1L)).thenReturn(channels);
        List<DmsMessageCostBudget> budgets = new ArrayList<>();
        budgets.add(budget("TENANT", "*"));
        budgets.add(budget("CHANNEL", "SMS"));
        ServiceSmsReadinessService.ALLOWED_EVENTS.forEach(event -> budgets.add(budget("EVENT", event)));
        when(budgetDao.selectList(1L)).thenReturn(budgets);
        when(authorizationDao.countActiveConsent(eq(1L), eq("SMS"), anyString(), any())).thenReturn(3);

        var status = service.evaluate(1L);

        assertTrue(status.isReadyForMemberOptIn());
        assertEquals(6, status.getApprovedTemplateCount());
        assertEquals(6, status.getEnabledEventCount());
        assertEquals(8, status.getConfiguredBudgetCount());
        assertEquals(8, status.getRequiredBudgetCount());
        assertEquals(3, status.getActiveAuthorizationCount());
        assertTrue(status.getItems().stream().allMatch(item -> item.passed()));
    }

    @Test
    void oldPrivacyTextOrOneMissingBudgetKeepsMemberOptInClosed() {
        DmsTenant tenant = new DmsTenant();
        tenant.setPrivacyPolicy("仅说明验证码短信");
        tenant.setThirdPartyServices("阿里云短信服务");
        when(tenantDao.selectById(1L)).thenReturn(tenant);
        external.setEnabled(true);
        external.setWorkerEnabled(true);
        sms.setEnabled(true);
        sms.setAccessKeyId("configured");
        sms.setAccessKeySecret("configured");
        sms.setSignName("审核签名");
        sms.setReceiptSecret("configured");
        ServiceSmsReadinessService.ALLOWED_EVENTS.forEach(event -> sms.getTemplates().put(event, "approved-" + event));
        DmsMessageChannelConfig channel = new DmsMessageChannelConfig();
        channel.setEventType("ORDER_SHIPPED");
        channel.setSmsEnabled(1);
        when(channelDao.selectList(1L)).thenReturn(List.of(channel));
        when(budgetDao.selectList(1L)).thenReturn(List.of(budget("TENANT", "*"), budget("CHANNEL", "SMS")));

        var status = service.evaluate(1L);

        assertFalse(status.isReadyForMemberOptIn());
        assertTrue(status.getItems().stream().anyMatch(item -> "LEGAL".equals(item.code()) && !item.passed()));
        assertTrue(status.getItems().stream().anyMatch(item -> "BUDGETS".equals(item.code()) && !item.passed()));
    }

    private DmsMessageCostBudget budget(String type, String key) {
        DmsMessageCostBudget value = new DmsMessageCostBudget();
        value.setScopeType(type);
        value.setScopeKey(key);
        value.setEnabled(1);
        value.setDailyLimit(BigDecimal.TEN);
        value.setMonthlyLimit(BigDecimal.valueOf(100));
        return value;
    }
}
