package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ProductReviewStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0-隐藏，1-恢复展示。 */
    @NotNull(message = "请选择评价状态")
    @Min(value = 0, message = "评价状态只能为隐藏或展示")
    @Max(value = 1, message = "评价状态只能为隐藏或展示")
    private Integer status;
    @Size(max = 255, message = "原因不能超过255字")
    private String reason;
}
