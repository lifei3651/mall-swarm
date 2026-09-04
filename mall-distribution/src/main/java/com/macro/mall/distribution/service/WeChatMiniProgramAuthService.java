package com.macro.mall.distribution.service;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.config.WeChatPayProperties;
import com.macro.mall.distribution.dto.WeChatMiniProgramLoginDTO;
import com.macro.mall.distribution.entity.DmsWechatMiniProgramIdentity;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramLoginVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramRuntimeVO;
import com.macro.mall.distribution.wechat.WeChatMiniProgramGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WeChatMiniProgramAuthService {

    private final WeChatMiniProgramProperties properties;
    private final WeChatMiniProgramGateway gateway;
    private final WeChatMiniProgramAccountService accountService;
    private final WeChatSubscriptionService subscriptionService;
    private final WeChatPayProperties weChatPayProperties;

    public WeChatMiniProgramRuntimeVO runtime() {
        WeChatMiniProgramRuntimeVO view = new WeChatMiniProgramRuntimeVO();
        view.setEnabled(properties.loginReady());
        view.setPhoneAuthorizationEnabled(properties.phoneAuthorizationReady());
        view.setPrivacyConsentVersion(properties.getPrivacyConsentVersion());
        // 支付开关仍由独立支付配置接口返回；这里不把拥有AppID误报成已完成资金联调。
        view.setPaymentEnabled(false);
        view.setSubscribeMessageEnabled(subscriptionService.ready());
        view.setShippingInfoEnabled(properties.shippingInfoReady() && weChatPayProperties.isConfigured());
        view.setSubscriptionTemplates(subscriptionService.publicTemplates());
        return view;
    }

    public WeChatMiniProgramLoginVO login(WeChatMiniProgramLoginDTO dto) {
        if (!properties.loginReady()) Asserts.fail("当前客户尚未开通微信小程序登录");
        if (!properties.getPrivacyConsentVersion().equals(dto.getPrivacyConsentVersion())) {
            Asserts.fail("隐私政策已更新，请重新阅读并授权");
        }
        WeChatMiniProgramGateway.LoginIdentity wxIdentity = gateway.exchangeLoginCode(dto.getLoginCode());
        DmsWechatMiniProgramIdentity existing = accountService.find(wxIdentity.openId());
        if (existing != null) {
            return success(accountService.loginExisting(existing, wxIdentity.unionId(),
                    dto.getPrivacyConsentVersion()));
        }
        if (dto.getPhoneCode() == null || dto.getPhoneCode().isBlank()) {
            WeChatMiniProgramLoginVO view = new WeChatMiniProgramLoginVO();
            view.setPhoneAuthorizationRequired(true);
            return view;
        }
        if (!properties.phoneAuthorizationReady()) {
            Asserts.fail("当前客户尚未开通微信手机号快捷验证");
        }
        WeChatMiniProgramGateway.PhoneNumber wxPhone = gateway.exchangePhoneCode(dto.getPhoneCode());
        String countryCode = wxPhone.countryCode() == null ? "" : wxPhone.countryCode().trim();
        if (!(countryCode.isEmpty() || "86".equals(countryCode))
                || !PhoneNumberUtils.isValidMainlandMobile(wxPhone.phoneNumber())) {
            Asserts.fail("当前商城仅支持中国大陆手机号");
        }
        String inviteCode = dto.getInviteCode() == null ? null
                : dto.getInviteCode().trim().toUpperCase(Locale.ROOT);
        return success(accountService.bind(wxIdentity.openId(), wxIdentity.unionId(),
                PhoneNumberUtils.normalize(wxPhone.phoneNumber()), inviteCode,
                dto.getPrivacyConsentVersion()));
    }

    private WeChatMiniProgramLoginVO success(WeChatMiniProgramAccountService.AccountLogin result) {
        ShopAuthVO auth = result.auth();
        WeChatMiniProgramLoginVO view = new WeChatMiniProgramLoginVO();
        view.setPhoneAuthorizationRequired(false);
        view.setNewMember(result.newMember());
        view.setAccessToken(auth.getToken());
        view.setExpireTime(auth.getExpireTime());
        view.setMember(auth.getMember());
        return view;
    }
}
