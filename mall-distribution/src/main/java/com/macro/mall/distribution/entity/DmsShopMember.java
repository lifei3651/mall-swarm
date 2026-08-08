package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsShopMember implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String phone;

    /** 登录账号；JSON 字段继续兼容历史 username，数据库列为 login_account。 */
    private String username;

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String salt;

    /** 独立支付密码（BCrypt），不复用登录密码。 */
    @JsonIgnore
    private String payPasswordHash;

    private Integer payPasswordFailedCount;

    private LocalDateTime payPasswordLockTime;

    private String nickname;

    private String avatarUrl;

    /** 邀请码（8位大写字母） */
    private String inviteCode;

    /** 邀请人 userId */
    private Long inviterId;

    private Integer status;

    /** 0-普通商城会员，1-系统内部资金账户（不参与会员统计、登录或团队关系）。 */
    private Integer systemAccount;

    private Integer failedLoginCount;
    private LocalDateTime lockTime;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
