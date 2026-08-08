package com.macro.mall.distribution.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShopAfterSaleReturnShipmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deliveryCompany;
    private String deliveryNo;
}
