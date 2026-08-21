package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class ErpShipmentCallbackDTO {
    @NotNull(message = "ERP客户租户不能为空")
    @Positive(message = "ERP客户租户不正确")
    private Long tenantId;
    @NotBlank(message = "ERP服务商编码不能为空")
    @Size(max = 64, message = "ERP服务商编码不能超过64个字符")
    private String providerCode;
    @ToString.Exclude
    @NotBlank(message = "ERP回调令牌不能为空")
    @Size(max = 2048, message = "ERP回调令牌过长")
    private String token;
    @NotBlank(message = "订单编号不能为空")
    @Size(max = 64, message = "订单编号不能超过64个字符")
    private String orderNo;
    @NotBlank(message = "物流公司不能为空")
    @Size(max = 50, message = "物流公司名称不能超过50个字")
    private String deliveryCompany;
    @NotBlank(message = "物流单号不能为空")
    @Size(max = 64, message = "物流单号不能超过64个字符")
    private String deliveryNo;
    @Positive(message = "发货数量必须大于0")
    private Integer shipmentQuantity;
}
