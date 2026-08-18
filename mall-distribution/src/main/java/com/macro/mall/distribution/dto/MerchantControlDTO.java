package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 平台对商户不同业务能力分别控制，避免一个“停用”同时阻断历史订单履约。 */
@Data
public class MerchantControlDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank private String accountStatus;
    @NotBlank private String businessStatus;
    @NotBlank private String fulfillmentStatus;
    @NotBlank private String withdrawalStatus;
    @NotBlank private String settlementStatus;
    @NotBlank private String depositStatus;
    @NotBlank private String auditStatus;
    @NotBlank private String exitStatus;
    @NotBlank
    @Size(max = 256, message = "状态调整原因不能超过256个字符")
    private String reason;
}
