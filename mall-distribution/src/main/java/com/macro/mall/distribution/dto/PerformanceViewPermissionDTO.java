package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class PerformanceViewPermissionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long agentId;

    private Long userId;

    /** 登录账号或手机号。 */
    @Size(max = 64, message = "会员查询条件不能超过64个字")
    private String memberKey;

    @Size(max = 64, message = "会员名称不能超过64个字")
    private String agentName;

    @Min(value = 0, message = "权限状态不正确")
    @Max(value = 1, message = "权限状态不正确")
    private Integer enabled;

    @Size(max = 500, message = "备注不能超过500个字")
    private String remark;
}
