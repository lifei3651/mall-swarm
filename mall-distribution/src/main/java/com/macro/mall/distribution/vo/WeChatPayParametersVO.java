package com.macro.mall.distribution.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class WeChatPayParametersVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String paymentNo;
    private String timeStamp;
    private String nonceStr;
    private String packageValue;
    private String signType;
    private String paySign;
}
