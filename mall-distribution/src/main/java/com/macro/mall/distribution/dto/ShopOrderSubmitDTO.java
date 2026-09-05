package com.macro.mall.distribution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ShopOrderSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long agentId;

    private String inviteCode;

    private Long addressId;

    @Size(max = 30, message = "收货人不能超过30个字")
    private String receiverName;

    private String receiverPhone;

    @Size(max = 512, message = "收货地址不能超过512个字")
    private String receiverAddress;

    @Size(max = 64, message = "省份不能超过64个字")
    private String receiverProvince;

    @Size(max = 64, message = "城市不能超过64个字")
    private String receiverCity;

    @Size(max = 64, message = "区县不能超过64个字")
    private String receiverDistrict;

    @Size(max = 200, message = "详细地址不能超过200个字")
    private String receiverDetailAddress;

    private String payType;

    @Size(max = 500, message = "订单备注不能超过500个字")
    private String remark;

    @Pattern(regexp = "NORMAL|REPURCHASE|FLASH_SALE", message = "订单业务类型不正确")
    private String businessType;

    private Long businessSourceId;

    /** 服务端大额支付验证验证码，业务类型固定为6。 */
    @lombok.ToString.Exclude
    private String smsCode;

    @NotEmpty(message = "订单商品不能为空")
    @Size(max = 200, message = "单次订单最多200项商品，请拆分下单")
    @Valid
    private List<ShopOrderItemDTO> items;
}
