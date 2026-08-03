package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PerformanceViewPermissionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long agentId;

    private Long userId;

    /** 登录账号或手机号。 */
    private String memberKey;

    private String agentName;

    private Integer enabled;

    private String remark;
}
