package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.RealNameVerificationProperties;
import com.macro.mall.distribution.dao.DmsMemberRealNameDao;
import com.macro.mall.distribution.dto.RealNameVerifyDTO;
import com.macro.mall.distribution.entity.DmsMemberRealName;
import com.macro.mall.distribution.entity.DmsMemberRealNameAttempt;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.identity.MainlandIdCard;
import com.macro.mall.distribution.identity.RealNameVerificationProvider;
import com.macro.mall.distribution.identity.RealNameVerificationResult;
import com.macro.mall.distribution.security.EncryptedStringTypeHandler;
import com.macro.mall.distribution.service.RealNameVerificationService;
import com.macro.mall.distribution.vo.RealNameStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealNameVerificationServiceImpl implements RealNameVerificationService {
    private static final int VERIFIED = 1;
    private static final String CONSENT_VERSION = "REAL_NAME_SENSITIVE_INFO_V1";

    private final DmsMemberRealNameDao realNameDao;
    private final RealNameVerificationProvider provider;
    private final RealNameVerificationProperties properties;

    @Override
    public RealNameStatusVO getStatus(DmsShopMember member) {
        DmsMemberRealName record = find(member);
        return toStatus(record);
    }

    @Override
    public RealNameStatusVO verify(DmsShopMember member, RealNameVerifyDTO dto) {
        requireMember(member);
        DmsMemberRealName existing = find(member);
        if (isVerifiedRecord(existing)) return toStatus(existing);
        if (dto == null || !Boolean.TRUE.equals(dto.getSensitiveInfoConsent())) {
            Asserts.fail("请先阅读并同意实名认证授权");
        }
        if (!EncryptedStringTypeHandler.isKeyConfigured() || !EncryptedStringTypeHandler.isWriteEnabled()) {
            Asserts.fail("实名认证安全配置尚未完成，请联系客服");
        }
        int maxAttempts = Math.max(1, properties.getDailyMaxAttemptsPerAccount());
        if (realNameDao.countAttemptsSince(tenantId(), member.getId(), LocalDateTime.now().toLocalDate().atStartOfDay()) >= maxAttempts) {
            Asserts.fail("今日实名认证次数已达上限，请明日再试");
        }
        String realName = dto.getRealName() == null ? "" : dto.getRealName().trim();
        String idCard = MainlandIdCard.normalize(dto.getIdCard());
        if (realName.isBlank() || realName.length() > 64) Asserts.fail("请输入正确的真实姓名");
        if (!MainlandIdCard.isValid(idCard)) Asserts.fail("请输入正确的18位身份证号");

        RealNameVerificationResult result = provider.verify(realName, idCard);
        saveAttempt(member, result);
        if (!result.matched()) Asserts.fail(messageFor(result.resultCode()));

        LocalDateTime now = LocalDateTime.now();
        DmsMemberRealName verified = new DmsMemberRealName();
        verified.setTenantId(tenantId());
        verified.setMemberId(member.getId());
        verified.setUserId(member.getUserId());
        verified.setStatus(VERIFIED);
        verified.setRealName(realName);
        verified.setIdCard(idCard);
        verified.setProvider("TENCENT");
        verified.setProviderRequestId(result.requestId());
        verified.setConsentVersion(CONSENT_VERSION);
        verified.setConsentTime(now);
        verified.setVerifiedTime(now);
        try {
            realNameDao.insert(verified);
        } catch (DuplicateKeyException duplicate) {
            verified = find(member);
        }
        log.info("会员实名认证成功: tenantId={}, memberId={}, userId={}, providerRequestId={}",
                tenantId(), member.getId(), member.getUserId(), result.requestId());
        return toStatus(verified);
    }

    @Override
    public DmsMemberRealName requireEligible(DmsShopMember member, String actionName) {
        DmsMemberRealName record = find(member);
        String action = actionName == null || actionName.isBlank() ? "资金操作" : actionName;
        if (!isVerifiedRecord(record)) Asserts.fail("完成实名认证后才可以" + action);
        if (!MainlandIdCard.isAdult(record.getIdCard())) Asserts.fail("未满18周岁暂不能" + action);
        return record;
    }

    @Override
    public boolean isVerified(DmsShopMember member) {
        return isVerifiedRecord(find(member));
    }

    private void saveAttempt(DmsShopMember member, RealNameVerificationResult result) {
        DmsMemberRealNameAttempt attempt = new DmsMemberRealNameAttempt();
        attempt.setTenantId(tenantId());
        attempt.setMemberId(member.getId());
        attempt.setUserId(member.getUserId());
        attempt.setProvider("TENCENT");
        attempt.setResultCode(result.resultCode());
        attempt.setMatched(result.matched() ? 1 : 0);
        attempt.setProviderRequestId(result.requestId());
        realNameDao.insertAttempt(attempt);
    }

    private DmsMemberRealName find(DmsShopMember member) {
        requireMember(member);
        return realNameDao.selectByMemberId(tenantId(), member.getId());
    }

    private RealNameStatusVO toStatus(DmsMemberRealName record) {
        RealNameStatusVO status = new RealNameStatusVO();
        boolean verified = isVerifiedRecord(record);
        status.setVerified(verified);
        status.setAdult(verified && MainlandIdCard.isAdult(record.getIdCard()));
        status.setVerificationAvailable(properties.isReady());
        if (verified) {
            status.setMaskedRealName(maskName(record.getRealName()));
            status.setMaskedIdCard("**************" + record.getIdCard().substring(14));
            status.setVerifiedTime(record.getVerifiedTime());
        }
        return status;
    }

    private boolean isVerifiedRecord(DmsMemberRealName record) {
        return record != null && Integer.valueOf(VERIFIED).equals(record.getStatus());
    }

    private String messageFor(String code) {
        return switch (code == null ? "" : code) {
            case "-1" -> "姓名与身份证号不一致，请核对后重试";
            case "-2" -> "身份证号格式不正确";
            case "-3" -> "姓名格式不正确";
            case "-5" -> "暂未查询到该身份信息，请核对或联系客服";
            case "-7" -> "今日实名认证次数已达上限，请明日再试";
            default -> "实名认证服务繁忙，请稍后重试";
        };
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "*";
        String trimmed = name.trim();
        if (trimmed.length() == 1) return "*";
        return "*".repeat(trimmed.length() - 1) + trimmed.substring(trimmed.length() - 1);
    }

    private void requireMember(DmsShopMember member) {
        if (member == null || member.getId() == null || member.getUserId() == null) Asserts.unauthorized("请先登录");
    }

    private Long tenantId() {
        return TenantContext.getTenantId();
    }
}
