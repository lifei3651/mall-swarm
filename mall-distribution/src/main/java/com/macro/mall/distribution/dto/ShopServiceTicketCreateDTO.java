package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopServiceTicketCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请选择问题类型")
    @Size(max = 32, message = "问题类型不正确")
    private String type;

    @NotBlank(message = "请填写问题标题")
    @Size(max = 100, message = "问题标题不能超过100个字")
    private String subject;

    @NotBlank(message = "请详细说明需要协助的问题")
    @Size(max = 1000, message = "问题说明不能超过1000个字")
    private String content;

    @Positive(message = "订单ID不正确")
    private Long orderId;

    @Positive(message = "售后单ID不正确")
    private Long afterSaleId;
}
