package com.macro.mall.distribution.service;

import cn.hutool.crypto.SecureUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsWechatMiniProgramIdentityDao;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.vo.ShopAuthVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WeChatMiniProgramAccountService {

    private final DmsWechatMiniProgramIdentityDao identityDao;
    private final DmsShopMemberDao memberDao;
    private final ShopAuthService shopAuthService;
    private final WeChatMiniProgramProperties properties;

    @Transactional(readOnly = true)
    public DmsWechatMiniProgramIdentity find(String openId) {
        return identityDao.selectActive(TenantContext.getTenantId(), appIdHash(), openIdHash(openId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountLogin loginExisting(DmsWechatMiniProgramIdentity identity, String unionId,
                                      String privacyConsentVersion) {
        if (identity == null) Asserts.fail("微信账号尚未绑定商城会员");
        ShopAuthVO auth = shopAuthService.loginWechatMember(identity.getMemberId());
        identity.setTenantId(TenantContext.getTenantId());
        identity.setUnionId(unionId);
        identity.setUnionIdHash(hashNullable(unionId));
        identity.setPrivacyConsentVersion(privacyConsentVersion);
        identity.setPrivacyConsentTime(LocalDateTime.now());
        identity.setLastLoginTime(LocalDateTime.now());
        if (identityDao.updateLogin(identity) != 1) Asserts.fail("微信账号状态已变化，请重新登录");
        return new AccountLogin(auth, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public AccountLogin bind(String openId, String unionId, String verifiedPhone, String inviteCode,
                             String privacyConsentVersion) {
        Long tenantId = TenantContext.getTenantId();
        String appIdHash = appIdHash();
        String openIdHash = openIdHash(openId);
        DmsWechatMiniProgramIdentity alreadyBound = identityDao.selectActive(tenantId, appIdHash, openIdHash);
        if (alreadyBound != null) {
            return loginExisting(alreadyBound, unionId, privacyConsentVersion);
        }

        DmsShopMember existingMember = memberDao.selectByPhone(verifiedPhone);
        if (existingMember != null) {
            DmsWechatMiniProgramIdentity memberIdentity = identityDao.selectByMember(
                    tenantId, appIdHash, existingMember.getId());
            if (memberIdentity != null && !Objects.equals(memberIdentity.getOpenIdHash(), openIdHash)) {
                Asserts.fail("该手机号已绑定其他微信账号，请使用原微信或联系客服处理");
            }
        }

        boolean newMember = existingMember == null;
        ShopAuthVO auth = shopAuthService.loginOrRegisterWechat(verifiedPhone, inviteCode);
        DmsWechatMiniProgramIdentity identity = new DmsWechatMiniProgramIdentity();
        identity.setTenantId(tenantId);
        identity.setMemberId(auth.getMember().getId());
        identity.setUserId(auth.getMember().getUserId());
        identity.setAppIdHash(appIdHash);
        identity.setOpenIdHash(openIdHash);
        identity.setUnionIdHash(hashNullable(unionId));
        identity.setOpenId(openId);
        identity.setUnionId(unionId);
        identity.setPrivacyConsentVersion(privacyConsentVersion);
        identity.setPrivacyConsentTime(LocalDateTime.now());
        identity.setPhoneAuthorizedTime(LocalDateTime.now());
        identity.setStatus(1);
        identity.setLastLoginTime(LocalDateTime.now());
        if (identityDao.insert(identity) != 1) Asserts.fail("微信账号绑定失败，请重新登录");
        return new AccountLogin(auth, newMember);
    }

    private String appIdHash() {
        return SecureUtil.sha256(properties.getAppId().trim());
    }

    private String openIdHash(String openId) {
        return SecureUtil.sha256(properties.getAppId().trim() + "\u0000" + openId);
    }

    private String hashNullable(String value) {
        return value == null || value.isBlank() ? null : SecureUtil.sha256(value);
    }

    public record AccountLogin(ShopAuthVO auth, boolean newMember) {
    }
}
