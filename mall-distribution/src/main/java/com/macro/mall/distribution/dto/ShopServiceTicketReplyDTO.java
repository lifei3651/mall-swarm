package com.macro.mall.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShopServiceTicketReplyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写回复内容")
    @Size(max = 1000, message = "回复内容不能超过1000个字")
    private String content;
}
