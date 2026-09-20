package com.macro.mall.distribution.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前会员可见的账号与直属邀请关系摘要。
 *
 * <p>不返回邀请人的手机号、登录账号或内部编号，避免在公开商城泄露他人身份信息。</p>
 */
@Data
public class ShopAccountIdentityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** PHONE：手机号账号；CUSTOM：已设置独立登录账号。 */
    private String accountMode;

    /** 本人可见的账号展示文案。 */
    private String accountDisplay;

    /** 是否仍可首次设置独立登录账号。 */
    private Boolean canSetupLoginAccount;

    /** BOUND：已绑定；NONE：普通入口注册未绑定；INVALID：历史关系待核验。 */
    private String inviterStatus;

    /** 仅在 BOUND 时返回邀请人的公开昵称。 */
    private String inviterName;
}
