package com.macro.mall.distribution.wechat;

public interface WeChatMiniProgramGateway {

    LoginIdentity exchangeLoginCode(String code);

    PhoneNumber exchangePhoneCode(String code);

    record LoginIdentity(String openId, String unionId) {
    }

    record PhoneNumber(String phoneNumber, String countryCode) {
    }
}
