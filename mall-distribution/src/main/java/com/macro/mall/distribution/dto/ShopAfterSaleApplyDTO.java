package com.macro.mall.distribution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ShopAfterSaleApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotNull(message = "请选择售后类型")
    @Min(value = 1, message = "售后类型不正确")
    @Max(value = 2, message = "售后类型不正确")
    private Integer applyType;

    private BigDecimal refundAmount;

    /** 实际申请退回的订单商品和数量；退款金额由服务端计算。 */
    @NotEmpty(message = "请选择实际退回的商品和数量")
    @Valid
    private List<ShopAfterSaleItemDTO> items;

    @NotBlank(message = "请选择申请原因")
    @Size(max = 170, message = "申请原因不能超过170个字")
    private String reason;

    private String proofImages;
}
