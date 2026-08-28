package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ProductReviewSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择评分")
    @Min(value = 1, message = "评分必须是1到5星")
    @Max(value = 5, message = "评分必须是1到5星")
    private Integer rating;
    /** 从订单待评价入口进入时锁定具体订单商品，避免复购同款评价写到另一笔订单。 */
    @Positive(message = "订单商品ID不正确")
    private Long orderItemId;
    @NotBlank(message = "请填写评价内容")
    @Size(max = 1000, message = "评价内容不能超过1000字")
    private String content;
}
