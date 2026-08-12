package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlashSaleActivitySaveDTO implements Serializable {

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 80, message = "活动名称不能超过80个字")
    private String activityName;

    @NotNull(message = "请选择秒杀商品")
    private Long productId;

    private Long skuId;

    @NotNull(message = "请填写秒杀价")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于0")
    private BigDecimal flashPrice;

    @DecimalMin(value = "0", message = "秒杀PV不能小于0")
    private BigDecimal flashPv;

    @NotNull(message = "请填写秒杀库存")
    @Min(value = 1, message = "秒杀库存至少为1")
    private Integer totalStock;

    @NotNull(message = "请填写每人限购")
    @Min(value = 1, message = "每人限购至少为1")
    private Integer perUserLimit;

    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    @Future(message = "结束时间必须晚于当前时间")
    private LocalDateTime endTime;

    private Integer status;
}
