package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ShopAfterSaleAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer status;

    @Size(max = 500, message = "审核说明不能超过500个字")
    private String auditRemark;

    private Long auditUserId;

    @Size(max = 64, message = "审核人名称不能超过64个字")
    private String auditUserName;
}
