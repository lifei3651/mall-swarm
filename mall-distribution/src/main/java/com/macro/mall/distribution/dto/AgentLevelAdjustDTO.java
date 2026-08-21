package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

@Data
public class AgentLevelAdjustDTO implements Serializable {
    @NotNull(message = "请选择会员级别")
    @Min(value = 0, message = "会员级别不正确")
    @Max(value = 8, message = "会员级别不正确")
    private Integer level;

    @NotBlank(message = "请输入调级原因")
    @Size(max = 300, message = "调级原因不能超过300个字")
    private String reason;
}
