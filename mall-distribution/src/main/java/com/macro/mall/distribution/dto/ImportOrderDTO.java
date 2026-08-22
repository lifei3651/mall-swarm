package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 导入订单DTO
 */
@Data
public class ImportOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    @Size(max = 64, message = "订单编号不能超过64个字符")
    private String orderNo;

    /** 订单金额 */
    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "订单金额超出系统支持范围")
    private BigDecimal orderAmount;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 订单归属登录账号；兼容历史推广编号。 */
    @NotBlank(message = "订单归属登录账号不能为空")
    @Size(max = 64, message = "订单归属登录账号不能超过64个字符")
    private String ownerAgentCode;

    /** 商品名称 */
    @Size(max = 200, message = "商品名称不能超过200个字符")
    private String productName;

    /** 商品数量 */
    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于0")
    private Integer quantity;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
