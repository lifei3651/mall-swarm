package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class ShopOrderShipDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写物流公司")
    @Size(max = 50, message = "物流公司名称不能超过50个字")
    private String deliveryCompany;

    @NotBlank(message = "请填写物流单号")
    @Size(min = 4, max = 64, message = "物流单号长度需要4至64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "物流单号只能包含字母、数字、下划线和短横线")
    private String deliveryNo;

    @Positive(message = "发货数量必须大于0")
    private Integer shipmentQuantity;
}
