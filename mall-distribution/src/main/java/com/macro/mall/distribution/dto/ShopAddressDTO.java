package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopAddressDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "请输入收货人")
    @Size(max = 30, message = "收货人不能超过30个字")
    private String receiverName;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String receiverPhone;

    @NotBlank(message = "请选择省份")
    private String province;

    @NotBlank(message = "请选择城市")
    private String city;

    @NotBlank(message = "请选择区/县")
    private String district;

    @NotBlank(message = "请输入详细地址")
    @Size(max = 200, message = "详细地址不能超过200个字")
    private String detailAddress;

    private Integer isDefault;
}
