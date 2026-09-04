package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class WeChatSubscriptionGrantDTO implements Serializable {
    @NotBlank(message = "授权请求编号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$", message = "授权请求编号格式不正确")
    private String requestId;

    @NotEmpty(message = "没有可记录的订阅授权")
    @Size(max = 5, message = "单次最多记录5个订阅模板")
    private List<@NotBlank @Size(max = 128) String> acceptedTemplateIds;
}
