package com.macro.mall.distribution.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderFinanceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    @Size(max = 64, message = "订单号不能超过64个字")
    private String orderNo;

    @DecimalMin(value = "0", message = "支付金额不能小于0")
    @Digits(integer = 14, fraction = 2, message = "支付金额格式不正确")
    private BigDecimal payAmount;

    @DecimalMin(value = "0", message = "商品成本不能小于0")
    @Digits(integer = 14, fraction = 2, message = "商品成本格式不正确")
    private BigDecimal productCost;

    @Size(max = 500, message = "备注不能超过500个字")
    private String remark;
}
