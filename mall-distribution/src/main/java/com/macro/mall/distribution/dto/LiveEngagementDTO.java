package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class LiveEngagementDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "访客标识不能为空")
    @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "访客标识格式不正确")
    private String visitorId;

    @NotBlank(message = "互动类型不能为空")
    @Pattern(regexp = "ENTER|HEARTBEAT|LEAVE|SHARE|PRODUCT_CLICK", message = "互动类型不正确")
    private String eventType;

    private Long productId;

    @Min(value = 0, message = "停留时长不能小于0")
    @Max(value = 86400, message = "单次停留时长不能超过24小时")
    private Integer durationSeconds;
}
