package com.macro.mall.distribution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String receiverProvince;

    private String receiverCity;

    private String receiverDistrict;

    private String receiverDetailAddress;

    private String payType;

    private String remark;

    @Pattern(regexp = "NORMAL|REPURCHASE|FLASH_SALE", message = "订单业务类型不正确")
    private String businessType;

    private Long businessSourceId;

    /** 服务端大额支付验证验证码，业务类型固定为6。 */
    @lombok.ToString.Exclude
    private String smsCode;

    @NotEmpty(message = "订单商品不能为空")
    @Valid
    private List<ShopOrderItemDTO> items;
}
