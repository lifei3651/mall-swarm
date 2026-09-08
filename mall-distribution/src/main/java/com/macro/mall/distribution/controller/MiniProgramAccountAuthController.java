package com.macro.mall.distribution.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.config.WeChatMiniProgramProperties;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.WeChatMiniProgramLoginVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Native bearer transport only. Browser endpoints retain their HttpOnly-only response.
 * Reuses the public shop's captcha/SMS/lockout/registration/invitation transactions.
 * Account login does NOT silently bind or replace a WeChat identity.
 */
@RestController
@RequestMapping("/shop/wechat-mini-program/auth")
@RequiredArgsConstructor
public class MiniProgramAccountAuthController {
    private final ShopAuthService auth;
    private final WeChatMiniProgramProperties properties;

    public record Login(@NotNull @Valid ShopLoginDTO credentials,
                        Boolean privacyAgreed, String privacyConsentVersion) {}
    public record Registration(@NotNull @Valid ShopRegisterDTO credentials,
                               Boolean privacyAgreed, String privacyConsentVersion) {}

    @PostMapping("/account-login")
    public CommonResult<WeChatMiniProgramLoginVO> login(@Valid @RequestBody Login input) {
        consent(input.privacyAgreed(), input.privacyConsentVersion());
        return CommonResult.success(nativeSession(auth.login(input.credentials(), "public"), false));
    }

    @PostMapping("/account-register")
    public CommonResult<WeChatMiniProgramLoginVO> register(@Valid @RequestBody Registration input) {
        consent(input.privacyAgreed(), input.privacyConsentVersion());
        return CommonResult.success(nativeSession(auth.registerPublic(input.credentials()), true));
    }

    private void consent(Boolean agreed, String version) {
        if (!Boolean.TRUE.equals(agreed)) Asserts.fail("请先阅读并同意用户服务协议和隐私政策");
        if (version == null || !version.equals(properties.getPrivacyConsentVersion())) {
            Asserts.fail("隐私政策已更新，请更新小程序后重新阅读并同意");
        }
    }

    private WeChatMiniProgramLoginVO nativeSession(ShopAuthVO source, boolean newMember) {
        WeChatMiniProgramLoginVO result = new WeChatMiniProgramLoginVO();
        result.setAccessToken(source.getToken());
        result.setMember(source.getMember());
        result.setExpireTime(source.getExpireTime());
        result.setNewMember(newMember);
        result.setPhoneAuthorizationRequired(false);
        return result;
    }
}
