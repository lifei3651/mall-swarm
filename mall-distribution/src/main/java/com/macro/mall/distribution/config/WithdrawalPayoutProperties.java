package com.macro.mall.distribution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 提现渠道独立开关；支付收款已启用也不会自动开启奖金转账。 */
@Data
@Component
@ConfigurationProperties(prefix = "shop.withdrawal-payout")
public class WithdrawalPayoutProperties {
    private boolean enabled;
    private boolean alipayEnabled;
    private boolean wechatEnabled;
    private String wechatTransferSceneId;
    private String wechatReportInfoType;
    private String wechatReportInfoContent;

    public boolean alipayReady() {
        return enabled && alipayEnabled;
    }

    public boolean wechatReady() {
        return enabled && wechatEnabled && present(wechatTransferSceneId)
                && present(wechatReportInfoType) && present(wechatReportInfoContent);
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
