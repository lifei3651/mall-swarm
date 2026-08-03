package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopOrderShipDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deliveryCompany;

    private String deliveryNo;

    private Integer shipmentQuantity;
}
