package com.macro.mall.distribution.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class TencentLiveCallbackDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("event_type")
    @NotNull(message = "直播回调事件类型不能为空")
    private Integer eventType;

    @JsonProperty("stream_id")
    @NotBlank(message = "直播流标识不能为空")
    @Size(max = 96, message = "直播流标识过长")
    private String streamId;

    @NotNull(message = "直播回调过期时间不能为空")
    private Long t;

    @NotBlank(message = "直播回调签名不能为空")
    @Size(min = 32, max = 32, message = "直播回调签名格式不正确")
    private String sign;
}
