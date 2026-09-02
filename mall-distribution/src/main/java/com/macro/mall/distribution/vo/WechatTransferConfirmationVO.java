package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

/** 仅返回给提现单本人、用于原生微信客户端拉起确认收款的短期参数。 */
@Data
public class WechatTransferConfirmationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long withdrawId;
    private String requestNo;
    private String state;
    private String mchId;
    private String appId;
    private String packageInfo;
}
