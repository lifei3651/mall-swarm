package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopAfterSaleReturnShipmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写退货物流公司")
    @Size(max = 50, message = "物流公司名称不能超过50个字")
    private String deliveryCompany;
    @NotBlank(message = "请填写退货运单号")
    @Size(min = 4, max = 64, message = "退货运单号长度需要4至64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "退货运单号只能包含字母、数字、下划线和短横线")
    private String deliveryNo;
}
