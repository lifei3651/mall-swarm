package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class PaymentPasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已设置支付密码时必填。 */
    @ToString.Exclude
    private String oldPassword;

    /** 6位数字独立支付密码。 */
    @ToString.Exclude
    private String newPassword;

    /** 首次设置支付密码时必须再次核验登录密码。 */
    @ToString.Exclude
    private String loginPassword;

    /** 首次设置支付密码时发送到账号绑定手机号的验证码。 */
    @ToString.Exclude
    private String smsCode;
}
