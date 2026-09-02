package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLogisticsPreferenceDTO {
    @NotBlank(message = "请选择默认物流公司")
    @Size(max = 50, message = "默认物流公司不能超过50个字符")
    private String company;
}
