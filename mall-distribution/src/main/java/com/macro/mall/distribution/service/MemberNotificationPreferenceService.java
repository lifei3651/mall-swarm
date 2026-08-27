package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsMessageRecipientAuthorizationDao;
import com.macro.mall.distribution.entity.DmsMessageRecipientAuthorization;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.notification.ServiceSmsReadinessService;
import com.macro.mall.distribution.vo.ServiceSmsPreferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberNotificationPreferenceService {
    public static final String CONSENT_VERSION = DmsMessageRecipientAuthorization.SERVICE_SMS_CONSENT_VERSION;
    private static final String CHANNEL = "SMS";
    private static final Set<String> SURFACES = Set.of("public", "team", "integrated");

    private final DmsMessageRecipientAuthorizationDao authorizationDao;
    private final ServiceSmsReadinessService readinessService;

    public ServiceSmsPreferenceVO status(DmsShopMember member) {
        requireMember(member);
        Long tenantId = TenantContext.getTenantId();
        DmsMessageRecipientAuthorization authorization = authorizationDao.selectByMemberChannel(tenantId, member.getId(), CHANNEL);
        String phone = normalizedPhone(member.getPhone());
        boolean samePhone = authorization != null && constantEquals(authorization.getEndpointHash(), sha256(phone));
        boolean currentConsent = authorization != null && CONSENT_VERSION.equals(authorization.getConsentVersion());
        boolean active = authorization != null && Integer.valueOf(1).equals(authorization.getAuthorized())
                && authorization.getRevokedTime() == null
                && (authorization.getExpiresAt() == null || authorization.getExpiresAt().isAfter(LocalDateTime.now()))
                && samePhone && currentConsent;
        boolean available = readinessService.canOfferMemberOptIn(tenantId);

        ServiceSmsPreferenceVO view = new ServiceSmsPreferenceVO();
        view.setAvailable(available);
        view.setEnabled(active);
        view.setMaskedPhone(mask(phone));
        view.setConsentVersion(CONSENT_VERSION);
        view.setAuthorizedTime(active ? authorization.getAuthorizedTime() : null);
        if (active) view.setStatusText("已接收订单、售后退款和账号安全服务短信");
        else if (authorization != null && Integer.valueOf(1).equals(authorization.getAuthorized()) && !samePhone)
            view.setStatusText("绑定手机号已变化，请重新开启服务短信");
        else if (!available) view.setStatusText("客户尚未开放服务短信，站内消息不受影响");
        else view.setStatusText("当前未开启服务短信");
        return view;
    }

    @Transactional
    public ServiceSmsPreferenceVO update(DmsShopMember member, boolean enabled, boolean consent, String surface) {
        requireMember(member);
        Long tenantId = TenantContext.getTenantId();
        String phone = normalizedPhone(member.getPhone());
        DmsMessageRecipientAuthorization current = authorizationDao.selectByMemberChannel(tenantId, member.getId(), CHANNEL);
        if (enabled) {
            if (!readinessService.canOfferMemberOptIn(tenantId)) Asserts.fail("服务短信尚未开放，请继续使用站内消息");
            if (!consent) Asserts.fail("请先确认服务短信说明");
            DmsMessageRecipientAuthorization next = preference(tenantId, member, phone, true, surface, LocalDateTime.now());
            persist(current, next);
        } else if (current != null) {
            DmsMessageRecipientAuthorization next = preference(tenantId, member, phone, false, surface, current.getAuthorizedTime());
            persist(current, next);
        }
        return status(member);
    }

    private void persist(DmsMessageRecipientAuthorization current, DmsMessageRecipientAuthorization next) {
        if (current != null) {
            authorizationDao.updatePreference(next);
            return;
        }
        try { authorizationDao.insert(next); }
        catch (DuplicateKeyException ignored) { authorizationDao.updatePreference(next); }
    }

    private DmsMessageRecipientAuthorization preference(Long tenantId, DmsShopMember member, String phone,
                                                         boolean enabled, String surface, LocalDateTime authorizedTime) {
        DmsMessageRecipientAuthorization value = new DmsMessageRecipientAuthorization();
        value.setTenantId(tenantId);
        value.setMemberId(member.getId());
        value.setChannel(CHANNEL);
        value.setEndpointHash(sha256(phone));
        value.setAuthorized(enabled ? 1 : 0);
        value.setAuthorizedTime(authorizedTime);
        value.setExpiresAt(null);
        value.setRevokedTime(enabled ? null : LocalDateTime.now());
        value.setConsentVersion(CONSENT_VERSION);
        value.setConsentSurface(SURFACES.contains(surface) ? surface : "legacy");
        return value;
    }

    private void requireMember(DmsShopMember member) { if (member == null || member.getId() == null) Asserts.unauthorized("请先登录"); }
    private String normalizedPhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("^1[3-9]\\d{9}$")) Asserts.fail("当前账号绑定手机号不正确，请先联系客服");
        return value;
    }
    private String mask(String phone) { return phone.substring(0, 3) + "****" + phone.substring(7); }
    private boolean constantEquals(String left, String right) {
        return left != null && right != null && java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256不可用", exception); }
    }
}
