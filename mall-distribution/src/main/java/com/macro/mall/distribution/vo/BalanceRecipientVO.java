package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class BalanceRecipientVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String memberName;

    /** 只用于转账前核对，禁止返回完整手机号。 */
    private String maskedPhone;

    /** 登录账号只返回脱敏结果，禁止作为登录凭证泄露。 */
    private String maskedLoginAccount;

    /** 稳定的商城会员编号，便于同名用户二次核对。 */
    private String memberNo;
}
