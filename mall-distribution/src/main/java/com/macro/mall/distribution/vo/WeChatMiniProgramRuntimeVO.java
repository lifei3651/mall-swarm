package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class WeChatMiniProgramRuntimeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled;

    private boolean phoneAuthorizationEnabled;

    private String privacyConsentVersion;

    /** 支付与订阅消息必须在各自正式网关完成后才能变为 true。 */
    private boolean paymentEnabled;

    private boolean subscribeMessageEnabled;
}
