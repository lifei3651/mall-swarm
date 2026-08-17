package com.macro.mall.distribution.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DmsAdminUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String passwordHash;

    private String salt;

    private String nickname;

    private String roleCode;

    private String permissions;

    /** 绑定后该后台账号仅能作为对应商户的商品工作台账号。 */
    private Long merchantId;

    private String merchantName;

    private Integer status;
    private Integer failedLoginCount;
    private LocalDateTime lockTime;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
