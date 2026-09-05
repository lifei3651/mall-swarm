package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
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
import com.macro.mall.distribution.vo.WeChatSubscriptionTemplateVO;
import com.macro.mall.distribution.vo.WeChatSubscriptionReadinessVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WeChatSubscriptionService {
    public static final String CONSENT_VERSION = "WECHAT_SUBSCRIBE_V1";
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "ORDER_SHIPPED", "AFTER_SALE_UPDATED", "REFUND_RESULT", "WITHDRAW_PAID");

    private final WeChatMiniProgramProperties properties;
    private final ExternalNotificationProperties externalProperties;
    private final DmsMiniProgramSubscriptionGrantDao grantDao;
    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final DmsMessageRecipientAuthorizationDao authorizationDao;

    public List<WeChatSubscriptionTemplateVO> publicTemplates() {
        return templateViews(null);
    }

    public boolean ready() {
        return properties.subscribeMessageReady()
                && externalProperties.isEnabled() && externalProperties.isWorkerEnabled();
    }

    public List<WeChatSubscriptionReadinessVO> readiness() {
        List<WeChatSubscriptionReadinessVO> rows = new ArrayList<>();
        String[][] events = {{"ORDER_SHIPPED", "订单发货提醒"}, {"AFTER_SALE_UPDATED", "售后进度提醒"},
                {"REFUND_RESULT", "退款结果提醒"}, {"WITHDRAW_PAID", "提现打款提醒"}};
        for (String[] event : events) {
            boolean configured = template(event[0]) != null;
            boolean runtime = configured && ready();
            String detail = !configured ? "模板编号或字段/页面映射尚未配置完整"
                    : !runtime ? "模板已配置，登录/订阅/外部发送运行门禁尚未全部开启"
                    : "配置就绪；仍需对应事件渠道、预算门禁、用户逐次授权及真机送达验收";
            rows.add(new WeChatSubscriptionReadinessVO(event[0], event[1], configured, runtime, detail));
        }
        return List.copyOf(rows);
    }

    @Transactional(readOnly = true)
    public List<WeChatSubscriptionTemplateVO> status(DmsShopMember member) {
        requireMember(member);
        return templateViews(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<WeChatSubscriptionTemplateVO> record(DmsShopMember member, WeChatSubscriptionGrantDTO input) {
        requireMember(member);
        if (!ready()) Asserts.fail("当前小程序尚未开通订阅消息");
        if (input == null || input.getAcceptedTemplateIds() == null || input.getAcceptedTemplateIds().isEmpty()
                || input.getAcceptedTemplateIds().size() > 3) Asserts.fail("单次只能记录1至3个订阅模板");
        Set<String> accepted = new HashSet<>(input.getAcceptedTemplateIds());
        if (accepted.size() != input.getAcceptedTemplateIds().size()) Asserts.fail("订阅模板不能重复");
        Map<String, WeChatMiniProgramProperties.SubscriptionTemplate> configured = properties.getSubscriptionTemplates();
        Set<String> allowed = new HashSet<>();
        configured.forEach((eventType, template) -> {
            if (SUPPORTED_EVENTS.contains(eventType) && template != null && template.ready()) {
                allowed.add(template.getTemplateId().trim());
            }
        });
        if (!allowed.containsAll(accepted)) Asserts.fail("订阅模板不属于当前小程序");

        Long tenantId = TenantContext.getTenantId();
        DmsWechatMiniProgramIdentity identity = identityDao.selectByMember(
                tenantId, appIdHash(), member.getId());
        if (identity == null || !member.getUserId().equals(identity.getUserId())
                || identity.getOpenIdHash() == null) Asserts.fail("请先使用当前微信重新登录小程序");
        LocalDateTime now = LocalDateTime.now();
        for (String templateId : accepted) {
            DmsMiniProgramSubscriptionGrant grant = new DmsMiniProgramSubscriptionGrant();
            grant.setTenantId(tenantId);
            grant.setMemberId(member.getId());
            grant.setUserId(member.getUserId());
            grant.setTemplateIdHash(templateHash(templateId));
            grant.setClientRequestId(input.getRequestId());
            grant.setAuthorizedTime(now);
            grantDao.insertIgnore(grant);
        }
        upsertAuthorization(member, identity, now);
        return templateViews(member);
    }

    @Transactional(rollbackFor = Exception.class)
    public DmsMiniProgramSubscriptionGrant reserve(Long tenantId, Long memberId, String templateId, Long taskId) {
        DmsMiniProgramSubscriptionGrant existing = grantDao.selectReservedByTask(tenantId, taskId);
        if (existing != null) return existing;
        DmsMiniProgramSubscriptionGrant available = grantDao.selectAvailableForUpdate(
                tenantId, memberId, templateHash(templateId));
        if (available == null || grantDao.reserve(tenantId, available.getId(), taskId) != 1) return null;
        available.setStatus("RESERVED");
        available.setReservedTaskId(taskId);
        return available;
    }

    @Transactional
    public void consume(Long tenantId, Long taskId) {
        grantDao.markConsumed(tenantId, taskId);
    }

    @Transactional
    public void invalidate(Long tenantId, Long taskId) {
        grantDao.markInvalid(tenantId, taskId);
    }

    public WeChatMiniProgramProperties.SubscriptionTemplate template(String eventType) {
        if (!SUPPORTED_EVENTS.contains(eventType)) return null;
        WeChatMiniProgramProperties.SubscriptionTemplate template = properties.getSubscriptionTemplates().get(eventType);
        return template != null && template.ready() ? template : null;
    }

    private List<WeChatSubscriptionTemplateVO> templateViews(DmsShopMember member) {
        if (!ready()) return List.of();
        Long tenantId = TenantContext.getTenantId();
        List<WeChatSubscriptionTemplateVO> rows = new ArrayList<>();
        properties.getSubscriptionTemplates().forEach((eventType, template) -> {
            if (!SUPPORTED_EVENTS.contains(eventType) || template == null || !template.ready()) return;
            Integer available = member == null ? null : grantDao.countAvailable(
                    tenantId, member.getId(), templateHash(template.getTemplateId()));
            rows.add(WeChatSubscriptionTemplateVO.builder()
                    .eventType(eventType).templateId(template.getTemplateId().trim())
                    .title(template.getTitle()).availableGrants(available).build());
        });
        return List.copyOf(rows);
    }

    private void upsertAuthorization(DmsShopMember member, DmsWechatMiniProgramIdentity identity, LocalDateTime now) {
        Long tenantId = TenantContext.getTenantId();
        DmsMessageRecipientAuthorization authorization = authorizationDao.selectByMemberChannel(
                tenantId, member.getId(), "MINI_PROGRAM");
        if (authorization == null) authorization = new DmsMessageRecipientAuthorization();
        authorization.setTenantId(tenantId);
        authorization.setMemberId(member.getId());
        authorization.setChannel("MINI_PROGRAM");
        authorization.setEndpointHash(identity.getOpenIdHash());
        authorization.setAuthorized(1);
        authorization.setAuthorizedTime(now);
        authorization.setExpiresAt(null);
        authorization.setRevokedTime(null);
        authorization.setConsentVersion(CONSENT_VERSION);
        authorization.setConsentSurface("mini-program");
        if (authorization.getId() != null) {
            authorizationDao.updatePreference(authorization);
            return;
        }
        try {
            authorizationDao.insert(authorization);
        } catch (DuplicateKeyException duplicate) {
            authorizationDao.updatePreference(authorization);
        }
    }

    private void requireMember(DmsShopMember member) {
        if (member == null || member.getId() == null || member.getUserId() == null) {
            Asserts.unauthorized("请先登录");
        }
    }

    private String appIdHash() {
        return SecureUtil.sha256(properties.getAppId().trim());
    }

    private String templateHash(String templateId) {
        return SecureUtil.sha256(properties.getAppId().trim() + "\u0000" + templateId.trim());
    }
}
