package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 提现审核DTO
 */
@Data
public class WithdrawAuditDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提现记录ID */
    @NotNull(message = "提现记录ID不能为空")
    private Long id;

    /** 审核状态：1-审核通过 4-审核拒绝 */
    @NotNull(message = "请选择审核结果")
    @Min(value = 1, message = "提现审核状态不正确")
    @Max(value = 4, message = "提现审核状态不正确")
    private Integer status;

    /** 审核备注 */
    @Size(max = 500, message = "审核备注不能超过500个字")
    private String auditRemark;

    /** 审核人ID */
    private Long auditUserId;

    /** 审核人名称 */
    @Size(max = 64, message = "审核人名称不能超过64个字")
    private String auditUserName;
}
