package com.macro.mall.distribution.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** 新建账号或由他人重置密码后，必须先自行修改密码才能进入业务后台。 */
    private Integer mustChangePassword;

    /** 一次性临时凭据到期时间；用户改成自己的正式密码后清空。 */
    private LocalDateTime credentialExpiresAt;

    /** 首次成功认证时原子写入；不清除强制改密标记，避免绕过首次改密。 */
    private LocalDateTime credentialConsumedAt;

    /** 当前操作账号在订单导入/导出中使用的默认物流公司。 */
    private String defaultLogisticsCompany;

    /** 只在创建或重置成功的当前响应中返回一次，从不写入数据库和日志。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String temporaryPassword;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
