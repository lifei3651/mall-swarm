package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 后台取消待结算佣金时的受控输入。 */
@Data
public class CommissionCancelDTO {

    @NotBlank(message = "请输入取消原因")
    @Size(max = 200, message = "取消原因不能超过200个字符")
    private String cancelReason;
}
