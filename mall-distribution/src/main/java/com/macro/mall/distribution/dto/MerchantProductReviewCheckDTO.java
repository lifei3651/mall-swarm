package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantProductReviewCheckDTO {
    @NotBlank(message = "审核项目不能为空")
    @Size(max = 40, message = "审核项目编码不能超过40个字符")
    private String code;

    @NotNull(message = "请选择每一项审核结果")
    private Boolean passed;
}
