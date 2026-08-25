package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 运营手动追加新品；durationDays 为0表示永久，否则只能设置30～365天。 */
@Data
public class ProductNewArrivalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "请选择是否加入新品")
    private Boolean enabled;

    @Min(value = 0, message = "新品展示时间不能小于0天")
    @Max(value = 365, message = "新品展示时间不能超过365天")
    private Integer durationDays;
}
