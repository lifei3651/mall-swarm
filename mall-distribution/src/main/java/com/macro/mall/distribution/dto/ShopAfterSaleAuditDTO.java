package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopAfterSaleAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer status;

    private String auditRemark;

    private Long auditUserId;

    private String auditUserName;
}
