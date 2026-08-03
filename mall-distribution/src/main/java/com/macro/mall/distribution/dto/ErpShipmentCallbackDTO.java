package com.macro.mall.distribution.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class ErpShipmentCallbackDTO {
    private String providerCode;
    @ToString.Exclude
    private String token;
    private String orderNo;
    private String deliveryCompany;
    private String deliveryNo;
    private Integer shipmentQuantity;
}
