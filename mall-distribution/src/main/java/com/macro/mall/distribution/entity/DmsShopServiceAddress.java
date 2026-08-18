package com.macro.mall.distribution.entity;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商城经营地址：用于商品发货或售后退货，不绑定具体会员。 */
@Data
public class DmsShopServiceAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tenantId;
    /** 空表示平台地址；非空表示商户私有地址。 */
    private Long merchantId;
    private String merchantName;
    /** 仅平台地址可设为1，表示明确共享给全部商户。 */
    private Integer sharedToMerchants;
    /** 1=发货地址，2=退货地址。 */
    private Integer addressType;
    @Size(max = 64, message = "地址名称不能超过64个字")
    private String addressLabel;
    @NotBlank(message = "请填写联系人")
    @Size(max = 64, message = "联系人不能超过64个字")
    private String contactName;
    @NotBlank(message = "请填写联系电话")
    @Size(max = 32, message = "联系电话不能超过32个字符")
    @Pattern(regexp = "^(?:1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8}|(?:400|800)-?\\d{3}-?\\d{4})$", message = "请填写正确的手机号或座机号码")
    private String contactPhone;
    @NotBlank(message = "请选择省份")
    @Size(max = 64, message = "省份名称不能超过64个字")
    private String province;
    @NotBlank(message = "请选择城市")
    @Size(max = 64, message = "城市名称不能超过64个字")
    private String city;
    @NotBlank(message = "请选择区/县")
    @Size(max = 64, message = "区/县名称不能超过64个字")
    private String district;
    @NotBlank(message = "请填写详细地址")
    @Size(max = 255, message = "详细地址不能超过255个字")
    private String detailAddress;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
