package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class MerchantProductReviewDecisionDTO {
    @NotNull(message = "请选择审核结果")
    private Boolean approved;

    @Size(max = 500, message = "审核说明不能超过500个字")
    private String remark;

    @Valid
    @NotNull(message = "请逐项完成商品审核")
    @Size(min = 6, max = 6, message = "请逐项完成全部六项商品审核")
    private List<MerchantProductReviewCheckDTO> checks;
}
