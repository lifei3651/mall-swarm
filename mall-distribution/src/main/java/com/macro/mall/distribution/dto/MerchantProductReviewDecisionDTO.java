package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantProductReviewDecisionDTO {
    @NotNull(message = "请选择审核结果")
    private Boolean approved;

    @Size(max = 500, message = "审核说明不能超过500个字")
    private String remark;
}
