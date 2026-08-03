package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import java.io.Serializable;

/** 后台创建会员；登录账号和手机号必填，密码可选。 */
@Data
public class AdminMemberCreateDTO implements Serializable {
    private String phone;
    private String username;
    @ToString.Exclude
    private String password;
    private String nickname;
    private Long inviterUserId;
    private Boolean activateDistribution;
    private Integer initialLevel;
    private String reason;
}
