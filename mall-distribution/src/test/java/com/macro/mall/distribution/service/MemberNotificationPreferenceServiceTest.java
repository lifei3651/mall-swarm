package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMessageChannelConfigDao;
import com.macro.mall.distribution.dao.DmsMessageCostBudgetDao;
import com.macro.mall.distribution.dao.DmsMessageRecipientAuthorizationDao;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.notification.AliyunNotificationSmsProperties;
import com.macro.mall.distribution.notification.ExternalNotificationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberNotificationPreferenceServiceTest {
    @Mock DmsMessageRecipientAuthorizationDao authorizationDao;
    @Mock DmsMessageChannelConfigDao channelDao;
    @Mock DmsMessageCostBudgetDao budgetDao;
    private ExternalNotificationProperties external;
    private AliyunNotificationSmsProperties sms;
    private MemberNotificationPreferenceService service;
    private DmsShopMember member;

    @BeforeEach
    void setup() {
        TenantContext.setTenantId(1L);
        external = new ExternalNotificationProperties();
        sms = new AliyunNotificationSmsProperties();
        service = new MemberNotificationPreferenceService(authorizationDao, channelDao, budgetDao, external, sms);
        member = new DmsShopMember();
        member.setId(8L);
        member.setPhone("13800138000");
    }

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void unavailableChannelCannotBeEnabledAndNeverReturnsPlainPhone() {
        var status = service.status(member);
        assertFalse(status.isAvailable());
        assertFalse(status.isEnabled());
        assertEquals("138****8000", status.getMaskedPhone());
        assertThrows(ApiException.class, () -> service.update(member, true, true, "public"));
        verify(authorizationDao, never()).insert(any());
    }

    @Test
    void explicitConsentStoresOnlyPhoneHashAndKnownSurface() {
        readyChannel();
        AtomicReference<DmsMessageRecipientAuthorization> stored = new AtomicReference<>();
        when(authorizationDao.selectByMemberChannel(1L, 8L, "SMS")).thenAnswer(ignored -> stored.get());
        when(authorizationDao.insert(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });

        var status = service.update(member, true, true, "team");

        assertTrue(status.isAvailable());
        assertTrue(status.isEnabled());
        assertNotNull(stored.get());
        assertEquals(64, stored.get().getEndpointHash().length());
        assertNotEquals(member.getPhone(), stored.get().getEndpointHash());
        assertEquals(MemberNotificationPreferenceService.CONSENT_VERSION, stored.get().getConsentVersion());
        assertEquals("team", stored.get().getConsentSurface());
        assertEquals(1, stored.get().getAuthorized());
    }

    @Test
    void changedPhoneInvalidatesOldAuthorization() {
        DmsMessageRecipientAuthorization old = new DmsMessageRecipientAuthorization();
        old.setAuthorized(1);
        old.setEndpointHash("0".repeat(64));
        old.setConsentVersion(MemberNotificationPreferenceService.CONSENT_VERSION);
        when(authorizationDao.selectByMemberChannel(1L, 8L, "SMS")).thenReturn(old);

        var status = service.status(member);

        assertFalse(status.isEnabled());
        assertTrue(status.getStatusText().contains("重新开启"));
    }

    @Test
    void authorizationCanBeRevokedEvenAfterChannelIsClosed() {
        AtomicReference<DmsMessageRecipientAuthorization> stored = new AtomicReference<>();
        DmsMessageRecipientAuthorization current = new DmsMessageRecipientAuthorization();
        current.setAuthorized(1);
        current.setAuthorizedTime(java.time.LocalDateTime.now().minusDays(1));
        current.setConsentVersion(MemberNotificationPreferenceService.CONSENT_VERSION);
        stored.set(current);
        when(authorizationDao.selectByMemberChannel(1L, 8L, "SMS")).thenAnswer(ignored -> stored.get());
        when(authorizationDao.updatePreference(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });

        var status = service.update(member, false, false, "public");

        assertFalse(status.isEnabled());
        assertEquals(0, stored.get().getAuthorized());
        assertNotNull(stored.get().getRevokedTime());
        verify(channelDao, never()).selectList(anyLong());
    }

    private void readyChannel() {
        external.setEnabled(true);
        external.setWorkerEnabled(true);
        sms.setEnabled(true);
        sms.setAccessKeyId("notification-ak");
        sms.setAccessKeySecret("notification-secret");
        sms.setSignName("灵启商城");
        sms.setReceiptSecret("receipt-secret");
        sms.getTemplates().put("ORDER_SHIPPED", "SMS_100000001");

        DmsMessageChannelConfig channel = new DmsMessageChannelConfig();
        channel.setEventType("ORDER_SHIPPED");
        channel.setSmsEnabled(1);
        when(channelDao.selectList(1L)).thenReturn(List.of(channel));
        when(budgetDao.selectList(1L)).thenReturn(List.of(
                budget("TENANT", "*"), budget("CHANNEL", "SMS"), budget("EVENT", "ORDER_SHIPPED")));
    }

    private DmsMessageCostBudget budget(String type, String key) {
        DmsMessageCostBudget budget = new DmsMessageCostBudget();
        budget.setScopeType(type);
        budget.setScopeKey(key);
        budget.setEnabled(1);
        budget.setDailyLimit(BigDecimal.TEN);
        budget.setMonthlyLimit(BigDecimal.valueOf(100));
        return budget;
    }
}
