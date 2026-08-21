package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LineChangeAuditDTO {
    /** 1通过，2拒绝 */
    @NotNull(message = "请选择审批结果")
    @Min(value = 1, message = "审批结果无效")
    @Max(value = 2, message = "审批结果无效")
    private Integer status;

    @NotBlank(message = "审批意见不能为空")
    @Size(max = 500, message = "审批意见不能超过500个字")
    private String remark;
}
