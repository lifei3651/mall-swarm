package com.macro.mall.distribution.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 提现审核DTO
 */
@Data
public class WithdrawAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提现记录ID */
    private Long id;

    /** 审核状态：1-审核通过 4-审核拒绝 */
    private Integer status;

    /** 审核备注 */
    private String auditRemark;

    /** 审核人ID */
    private Long auditUserId;

    /** 审核人名称 */
    private String auditUserName;
}
