package com.macro.mall.distribution.notification;

import com.macro.mall.distribution.dao.DmsMessageChannelConfigDao;
import com.macro.mall.distribution.dao.DmsMessageCostBudgetDao;
import com.macro.mall.distribution.dao.DmsMessageRecipientAuthorizationDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.distribution.entity.DmsMessageChannelConfig;
import com.macro.mall.distribution.entity.DmsMessageCostBudget;
import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.vo.ServiceSmsReadinessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceSmsReadinessService {
    public static final List<String> ALLOWED_EVENTS = List.of(
            "LOGIN_PASSWORD_CHANGED", "PAY_PASSWORD_CHANGED", "PHONE_CHANGED",
            "ORDER_SHIPPED", "AFTER_SALE_UPDATED", "REFUND_RESULT");
    private static final String CHANNEL = "SMS";

    private final DmsMessageChannelConfigDao channelDao;
    private final DmsMessageCostBudgetDao budgetDao;
    private final DmsMessageRecipientAuthorizationDao authorizationDao;
    private final DmsTenantDao tenantDao;
    private final ExternalNotificationProperties external;
    private final AliyunNotificationSmsProperties sms;

    public ServiceSmsReadinessVO evaluate(Long tenantId) {
        return evaluate(tenantId, true);
    }

    public boolean canOfferMemberOptIn(Long tenantId) {
        return evaluate(tenantId, false).isReadyForMemberOptIn();
    }

    private ServiceSmsReadinessVO evaluate(Long tenantId, boolean includeAuthorizationCount) {
        long actualTenantId = tenantId == null ? 1L : tenantId;
        DmsTenant tenant = tenantDao.selectById(actualTenantId);
        List<DmsMessageChannelConfig> channels = safe(channelDao.selectList(actualTenantId));
        List<DmsMessageCostBudget> budgets = safe(budgetDao.selectList(actualTenantId));

        boolean legalReady = tenant != null && contains(tenant.getPrivacyPolicy(), "服务短信")
                && contains(tenant.getPrivacyPolicy(), "消息中心")
                && contains(tenant.getThirdPartyServices(), "短信");
        boolean providerReady = !blank(sms.getAccessKeyId()) && !blank(sms.getAccessKeySecret())
                && !blank(sms.getSignName()) && !blank(sms.getReceiptSecret());
        int approvedTemplates = (int) ALLOWED_EVENTS.stream()
                .filter(event -> !blank(sms.getTemplates().get(event))).count();
        boolean templatesReady = approvedTemplates == ALLOWED_EVENTS.size();
        List<String> enabledEvents = channels.stream()
                .filter(value -> Integer.valueOf(1).equals(value.getSmsEnabled()))
                .map(DmsMessageChannelConfig::getEventType)
                .filter(ALLOWED_EVENTS::contains).distinct().toList();
        boolean eventsReady = !enabledEvents.isEmpty()
                && enabledEvents.stream().allMatch(event -> !blank(sms.getTemplates().get(event)));
        int requiredBudgets = 2 + enabledEvents.size();
        int configuredBudgets = (budgetReady(budgets, "TENANT", "*") ? 1 : 0)
                + (budgetReady(budgets, "CHANNEL", CHANNEL) ? 1 : 0)
                + (int) enabledEvents.stream().filter(event -> budgetReady(budgets, "EVENT", event)).count();
        boolean budgetsReady = configuredBudgets == requiredBudgets;
        boolean runtimeReady = external.isEnabled() && external.isWorkerEnabled() && sms.isEnabled();
        boolean ready = legalReady && providerReady && templatesReady && eventsReady && budgetsReady && runtimeReady;

        int activeAuthorizations = includeAuthorizationCount
                ? authorizationDao.countActiveConsent(actualTenantId, CHANNEL,
                DmsMessageRecipientAuthorization.SERVICE_SMS_CONSENT_VERSION, LocalDateTime.now()) : 0;
        List<ServiceSmsReadinessVO.Item> items = new ArrayList<>();
        items.add(item("LEGAL", "隐私与第三方说明", legalReady,
                legalReady ? "已说明服务短信用途、服务商和关闭路径" : "请先更新正式隐私政策和第三方服务说明"));
        items.add(item("PROVIDER", "短信服务商资料", providerReady,
                providerReady ? "独立账号、签名和回执资料已配置" : "等待客户提供独立账号、签名和回执资料"));
        items.add(item("TEMPLATES", "六类审核模板", templatesReady,
                "已配置 " + approvedTemplates + " / " + ALLOWED_EVENTS.size() + " 个审核模板"));
        items.add(item("EVENTS", "开放的服务事件", eventsReady,
                enabledEvents.isEmpty() ? "尚未选择任何服务短信事件" : "已选择 " + enabledEvents.size() + " 个低频服务事件"));
        items.add(item("BUDGETS", "费用硬上限", budgetsReady,
                "已配置 " + configuredBudgets + " / " + requiredBudgets + " 组日、月上限"));
        items.add(item("RUNTIME", "受控运行门禁", runtimeReady,
                runtimeReady ? "总门禁、发送器和短信适配器均已开启" : "真实验收前继续保持关闭"));

        ServiceSmsReadinessVO view = new ServiceSmsReadinessVO();
        view.setReadyForMemberOptIn(ready);
        view.setApprovedTemplateCount(approvedTemplates);
        view.setRequiredTemplateCount(ALLOWED_EVENTS.size());
        view.setEnabledEventCount(enabledEvents.size());
        view.setConfiguredBudgetCount(configuredBudgets);
        view.setRequiredBudgetCount(requiredBudgets);
        view.setActiveAuthorizationCount(activeAuthorizations);
        view.setItems(List.copyOf(items));
        return view;
    }

    private ServiceSmsReadinessVO.Item item(String code, String label, boolean passed, String detail) {
        return new ServiceSmsReadinessVO.Item(code, label, passed, detail);
    }

    private boolean budgetReady(List<DmsMessageCostBudget> budgets, String type, String key) {
        return budgets.stream().anyMatch(value -> type.equals(value.getScopeType()) && key.equals(value.getScopeKey())
                && Integer.valueOf(1).equals(value.getEnabled()) && positive(value.getDailyLimit())
                && positive(value.getMonthlyLimit()));
    }

    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private boolean contains(String value, String part) { return value != null && value.contains(part); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
