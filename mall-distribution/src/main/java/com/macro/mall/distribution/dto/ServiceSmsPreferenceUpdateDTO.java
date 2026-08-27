package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServiceSmsPreferenceUpdateDTO {
    @NotNull(message = "请选择是否接收服务短信")
    private Boolean enabled;
    /** 开启时必须由用户在当前页面明确确认；关闭时忽略。 */
    private Boolean consent;
}
