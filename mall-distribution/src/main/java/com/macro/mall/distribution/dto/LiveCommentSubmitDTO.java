package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class LiveCommentSubmitDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 300, message = "评论内容不能超过300个字")
    private String content;

    @NotBlank(message = "访客标识不能为空")
    @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "访客标识格式不正确")
    private String visitorId;
}
